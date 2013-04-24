package de.mobile.siteops.jolokia

import org.jolokia.client.J4pClient;
import org.jolokia.client.request.*;


import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map
import java.util.logging.Logger;

 def logToGraphite(String key, long value) {
  Map stats = new HashMap();
  stats.put(key, value);
  logToGraphite(stats);
 }

def logToGraphite(Map stats) {
  if (stats.isEmpty()) {
   return;
  }

  try {
   // String nodeIdentifier = java.net.InetAddress.getLocalHost().getHostName();
   String nodeIdentifier = "servers.cron46-1"
   logToGraphite(nodeIdentifier, stats);
  } catch (Throwable t) {
   println "Can't log to graphite: ${t}"
  }
 }

 def logToGraphite(String nodeIdentifier, Map stats) throws Exception {
  Long curTimeInSec = System.currentTimeMillis() / 1000;
  StringBuffer lines = new StringBuffer();
  for (Map.Entry entry : stats.entrySet()) {
   String key = nodeIdentifier + "." + entry.getKey();
   lines.append(key).append(" ").append(entry.getValue()).append(" ").append(curTimeInSec).append("\n"); //even the last line in graphite
  }
  logToGraphite(lines);
 }

def logToGraphite(StringBuffer lines) throws Exception {
  String msg = lines.toString();
  println "Writing to graphite: ${msg}"
  Socket socket = new Socket("10.46.22.142", 2003);
  try {
   Writer writer = new OutputStreamWriter(socket.getOutputStream());
   writer.write(msg);
   writer.flush();
   writer.close();
  } finally {
   socket.close();
  }
}


J4pClient j4pClient = new J4pClient("http://10.46.22.101:7777/jolokia");
 J4pReadRequest req = new J4pReadRequest("java.lang:type=Memory","HeapMemoryUsage")
 req.setPath("used");
 J4pReadResponse resp = j4pClient.execute(req);
logToGraphite("java.HeapMemoryUsage", resp.getValue())
 System.out.println(resp.getValue())