#!/bin/bash

if [ -z "$NEO4J_HOME" ]; then
  echo -e "\nMake sure NEO4J_HOME is set.\n"
  exit 1
fi

# use TP2: ./deploy.sh
# use TP3: ./deploy.sh -Dtp.version=3

mvn clean package -DskipTests $1

rm -rf $NEO4J_HOME/plugins/gremlin-plugin
unzip target/neo4j-gremlin-plugin-*-server-plugin.zip -d $NEO4J_HOME/plugins/gremlin-plugin
$NEO4J_HOME/bin/neo4j restart
