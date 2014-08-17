#!/bin/bash

PROJECT_VERSION=$(cat pom.xml | grep -Po '(?<=<version>).*(?=</version>)' | head -n1)
NEO4J_VERSION=$(cat pom.xml | grep -Po '(?<=<neo4j-version>).*(?=</neo4j-version>)' | head -n1)

API_JSON=$(printf '{"tag_name": "%s","target_commitish": "master","name": "Gremlin Plugin for Neo4j %s","body": "","draft": false,"prerelease": false}' $PROJECT_VERSION $NEO4J_VERSION)
UPLOAD_URL_TEMPLATE=$(curl -s --data "$API_JSON" https://api.github.com/repos/thinkaurelius/neo4j-gremlin-plugin/releases?access_token=$GITHUB_TOKEN | grep -Po '(?<="upload_url": ")[^"]*')

for TINKERPOP_VERSION in 2 3
do
  mvn clean package -Dtp.version=$TINKERPOP_VERSION
  FILENAME=$(ls target/*.zip)
  BASENAME=`basename $FILENAME`
  UPLOAD_URL=$(echo $UPLOAD_URL_TEMPLATE | sed "s@{?name}@?name=$BASENAME@")
  curl -s -H "Authorization: token $GITHUB_TOKEN" -H "Content-Type: application/zip" --data-binary @$FILENAME $UPLOAD_URL > /dev/null
done

