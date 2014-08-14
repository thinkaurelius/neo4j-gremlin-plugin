<img src="https://s3.amazonaws.com/aurelius-assets/neo4j-gremlin.png" alt="Neo4jGremlinPlugin" width="400" />

neo4j-gremlin-plugin
====================

The Neo4jGremlinPlugin provides the ability to query/traverse a [Neo4j](http://neo4j.org =100x) graph using [Gremlin](http://gremlin.tinkerpop.com) via Neo4j Server 2.x. For Neo4j Server 1.x, please see the following project by Peter Neubauer: [neo4j-contrib/gremlin-plugin](https://github.com/neo4j-contrib/gremlin-plugin)

## Configure Neo4j Server

Register the plugin in your ```$NEO4J_HOME/conf/neo4j-server.properties``` file. To do so, add this line:

```
org.neo4j.server.thirdparty_jaxrs_classes=com.thinkaurelius.neo4j.plugins=/tp
```

...or, if you already registered another plugin, modify the setting accordingly.

## Deploy into Neo4j Server

To build and deploy Neo4jGremlinPlugin, please use the bash commands below. If you wish to use existing binaries, download from the [releases](https://github.com/thinkaurelius/neo4j-gremlin-plugin/releases) section of this repository.

```sh
mvn clean package
unzip target/neo4j-gremlin-plugin-*-server-plugin.zip -d $NEO4J_HOME/plugins/gremlin-plugin
$NEO4J_HOME/bin/neo4j restart
```

## A first tests using curl

If everything went well, you should already see an empty success message when you access the Gremln REST endpoint.

```
$ curl -s -G http://localhost:7474/tp/gremlin/execute
{
    "success": true
}
```

## Parameters

| parameter  | format                          | description                                                |
| ---------- | ------------------------------- | ---------------------------------------------------------- |
| **script** | String                          | the Gremlin script to be evaluated                         |
| **params** | JSON object                     | a map of parameters to bind to the script engine           |
| **load**   | comma-separated list of Strings | a list of Gremlin scripts to execute prior to the 'script' |


### Notes

1. If only the load parameter and no script is given, the extension will return the result of the last loaded script.
2. Gremlin scripts must reside in ```$NEO4J_HOME/scripts```. The plugin will append a ```.gremlin``` extension to the given script name when it tries to load it.
3. Scripts given in the ```load``` parameter are loaded one time and then reside in the server side cache. If you modify a script after it has already been loaded and you want the changes to take effect, you have to restart the Neo4j Server.
4. All scripts have access to the Neo4j graph through the variable ```g```.
5. Last but not least: use ```params``` whenever possible.

## Examples

### Simple script

```
$ curl -s -G --data-urlencode 'script="Hello World!"' \
             http://localhost:7474/tp/gremlin/execute
{
    "results": [
        "Hello World!"
    ],
    "success": true
}
```

### Parameterized script

```
$ curl -s -G --data-urlencode 'script="Hello ${name}!"' \
             --data-urlencode 'params={"name":"Gremlin"}' \
             http://localhost:7474/tp/gremlin/execute
{
    "results": [
        "Hello Gremlin!"
    ],
    "success": true
}
```

### Parameterized script using a function defined in an external script

```
$ echo 'def sayHello(def name) { "Hello ${name}!" }' > $NEO4J_HOME/scripts/sayhello.gremlin
$ curl -s -G --data-urlencode 'script=sayHello(name)' \
             --data-urlencode 'params={"name":"Gremlin"}' \
             --data-urlencode 'load=sayhello' http://localhost:7474/tp/gremlin/execute
{
    "results": [
        "Hello Gremlin!"
    ],
    "success": true
}
```

### A script throwing an exception

```
$ curl -s -G --data-urlencode 'script=throw new Exception("something went wrong")' \
             http://localhost:7474/tp/gremlin/execute
{
    "errormessage": "javax.script.ScriptException: java.lang.Exception: something went wrong",
    "success": false
}
```

### Learning Gremlin

For more information on Gremlin, please visit the following locations.
* Gremlin Homepage [http://gremlin.tinkerpop.com](http://gremlin.tinkerpop.com)
* GremlinDocs [http://gremlindocs.com](http://gremlindocs.com)
* SQL2Gremlin [http://sql2gremlin.com](http://sql2gremlin.com)
