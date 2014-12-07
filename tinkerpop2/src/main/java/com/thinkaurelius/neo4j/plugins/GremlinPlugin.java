package com.thinkaurelius.neo4j.plugins;

import com.tinkerpop.blueprints.impls.neo4j2.Neo4j2Graph;
import com.tinkerpop.blueprints.util.io.graphson.GraphSONMode;
import com.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine;
import com.tinkerpop.pipes.util.structures.Pair;
import com.tinkerpop.rexster.gremlin.converter.JSONResultConverter;
import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.SimpleBindings;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Daniel Kuppitz (daniel at thinkaurelius.com)
 */
@Path("/gremlin")
public class GremlinPlugin {

    private final static Object LOCK = new Object();
    private final static JSONResultConverter RESULT_CONVERTER = new JSONResultConverter(GraphSONMode.NORMAL, 0, Long.MAX_VALUE, null);
    private final static ConcurrentMap<String, String> CACHED_SCRIPTS = new ConcurrentHashMap<>();
    private final static String SCRIPT_DIRECTORY = getScriptDirectory();

    private static volatile ScriptEngine engine;
    private static Neo4j2Graph neo4jGraph;

    private final GraphDatabaseService neo4j;
    private final Neo4j2Graph graph;

    private static String getScriptDirectory() {
        try {
            return Paths.get(GremlinPlugin.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParent().getParent().getParent() + File.separator + "scripts";
        } catch (URISyntaxException e) {
            return "." + File.separator + "scripts";
        }
    }

    public GremlinPlugin(@Context GraphDatabaseService database) {
        this.graph = getOrCreateGraph(this.neo4j = database);
    }

    private static Neo4j2Graph getOrCreateGraph(final GraphDatabaseService database) {
        if (neo4jGraph == null) {
            synchronized (LOCK) {
                if (neo4jGraph == null) {
                    neo4jGraph = new Neo4j2Graph(database);
                }
            }
        }
        return neo4jGraph;
    }

    private static ScriptEngine engine() {
        if (engine == null || CACHED_SCRIPTS.size() > 500) {
            synchronized (LOCK) {
                engine = new GremlinGroovyScriptEngine();
                CACHED_SCRIPTS.clear();
            }
        }
        return engine;
    }

    private static synchronized String readFile(final String fileName) throws IOException {
        final StringWriter stringWriter = new StringWriter();
        IOUtils.copy(new FileInputStream(new File(fileName)), stringWriter);
        return stringWriter.toString();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/execute")
    public Response execute(@QueryParam("script") final String script,
                            @QueryParam("load") final String load,
                            @QueryParam("params") final String params) {

        try (Transaction tx = neo4j.beginTx()) {

            Object result = null;

            final String[] loadScripts = load != null && !load.isEmpty() ? load.split(",") : null;
            final JSONObject parameters = params != null ? new JSONObject(params) : null;
            final ScriptEngine engine = engine();
            final Bindings bindings = createBindings(parameters);
            final Iterator<String> scriptsToRun = getScriptsToRun(loadScripts);

            while (scriptsToRun.hasNext()) {
                final String code = scriptsToRun.next();
                result = engine.eval(code, bindings);
            }

            if (script != null && !script.isEmpty()) {
                CACHED_SCRIPTS.putIfAbsent(script, script);
                result = engine.eval(script, bindings);
            }

            final Response response;

            if (result != null) {

                final Pair<JSONArray, Long> convertedResults = RESULT_CONVERTER.convert(result, false);
                final JSONArray results = convertedResults.getA();
                final HashMap<String, Object> resultMap = new HashMap<String, Object>() {{
                    put("success", true);
                    put("results", results);
                }};

                final JSONObject resultObject = new JSONObject(resultMap);
                response = Response.status(Response.Status.OK).entity(
                        resultObject.toString().getBytes(Charset.forName("UTF-8"))).build();
            } else {

                final HashMap<String, Object> resultMap = new HashMap<String, Object>() {{
                    put("success", true);
                }};

                final JSONObject resultObject = new JSONObject(resultMap);
                response = Response.status(Response.Status.OK).entity(
                        resultObject.toString().getBytes(Charset.forName("UTF-8"))).build();
            }

            tx.success();

            return response;

        } catch (final Exception e) {

            final HashMap<String, Object> resultMap = new HashMap<String, Object>() {{
                put("success", false);
                put("errormessage", e.getMessage());
            }};

            final JSONObject resultObject = new JSONObject(resultMap);
            return Response.status(Response.Status.BAD_REQUEST).entity(
                    resultObject.toString().getBytes(Charset.forName("UTF-8"))).build();
        }
    }

    private Bindings createBindings(final JSONObject params) throws JSONException {
        final Bindings bindings = new SimpleBindings() {{
            put("g", graph);
        }};
        if (params != null) {
            final Iterator keys = params.keys();
            while (keys.hasNext()) {
                final String key = (String) keys.next();
                bindings.put(key, params.get(key));
            }
        }
        return bindings;
    }

    private Iterator<String> getScriptsToRun(final String[] scripts) throws IOException {

        final List<String> scriptList = new ArrayList<>();

        if (scripts != null) {
            for (final String name : scripts) {
                final String locationAndScriptFile = SCRIPT_DIRECTORY + File.separator + name + ".gremlin";
                String script = CACHED_SCRIPTS.get(locationAndScriptFile);
                if (script == null) {
                    script = readFile(locationAndScriptFile);
                    synchronized (LOCK) {
                        CACHED_SCRIPTS.putIfAbsent(locationAndScriptFile, script);
                    }
                }
                scriptList.add(script);
            }
        }

        return scriptList.iterator();
    }
}
