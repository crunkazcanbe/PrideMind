package com.dogpound.pridemind.api;

import com.google.gson.JsonObject;

/**
 * Something the Mind can DO in Minecraft. Register with {@link Tools#register}; the agent sees name + args + description
 * and calls it with JSON arguments. Runs on the server thread, so it may touch the world freely.
 */
public interface Tool {
    /** how much a tool may change — callers are only offered tools at or below their level */
    enum Level { LOOK, NOTE, ACT, POWER }

    String name();
    /** argument summary for the model, e.g. "x:int y:int z:int" */
    String args();
    String description();
    Level level();
    /** the result as JSON (or any object Gson can turn into JSON) */
    Object run(ToolContext ctx, JsonObject args) throws Exception;
}
