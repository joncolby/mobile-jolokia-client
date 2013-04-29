package de.mobile.siteops.jolokia

import org.jolokia.client.J4pClient;
import org.jolokia.client.request.*
import org.jolokia.client.exception.J4pException;
import javax.management.InstanceNotFoundException;
import org.apache.commons.cli.Option
import javax.management.ObjectName;

private final DEBUG = false
private final VERSION = 1.0

def cli = new CliBuilder(usage:'java -cp <path/jarfile-name.jar> de.mobile.siteops.jolokia.JolokiaSearchNagiosClient parameters')
cli.with {
    cli.'?'(longOpt: 'help', 'usage information')
    h(longOpt: 'host', required: true, args: 1, 'remote host')
    p(longOpt: 'port', required: true, args: 1, 'remote port')
    b(longOpt: 'bean', required: true, args: 1, 'jmx bean name pattern')
    a(longOpt: 'attribute', required: false, args: Option.UNLIMITED_VALUES, type: String, valueSeparator: ',', 'attribute values to show in output (optional)')
    v(longOpt: 'version', required: false, 'version information (optional)')
    t(longOpt: 'testattribute', required: true, args: 1, 'attribute used to test nagios')
    m(longOpt: 'match', required: false, args: 1, type: String, 'result must match this string or script will exit with CRITICAL status. (optional)')
    w(longOpt: 'warn', required: false, args: 1, type: String, 'WARN threshold (optional)')
    c(longOpt: 'critical', required: false, args: 1, type: String, 'CRITICAL threshold (optional)')
    o(longOpt: 'objectname', required: false, type: Boolean, 'add objectname to output (optional)')
}

def argList = []
argList << args
 if (['-v','--version'].intersect(argList.flatten())) {
     println "version ${VERSION}"
     System.exit(0)
 }


def opt = cli.parse(args)
if (!opt) // usage already displayed by cli.parse()
  System.exit(2)

if (opt.'?')
{
  cli.usage()
  return
}

    def host = opt.h
    def port = opt.p
    def bean = opt.b
    def attributes = opt.as
    def attribute = opt.a
    def warning = opt.w
    def critical = opt.c
    def objectName = opt.o
    def testAttribute = opt.t
    def oKMap = [:]
    def warningMap = [:]
    def criticalMap = [:]
    def outputAttributes = [:]

    if (warning && !critical) {
        println "ERROR: critical must also be specified"
        System.exit(2)
    }

    if (critical && !warning) {
        println "ERROR: warning must also be specified"
        System.exit(2)
    }

    try {
        if(warning)
            warning = Double.valueOf(opt.w)
        if(critical)
            critical = Double.valueOf(opt.c)
    } catch (Exception e) {
        println "ERROR: " + e.message
        System.exit(2)
    }

    def match = opt.m

    def processCheckResult = { n,r ->
        if (match) {
            if (r.equals(match)) {
                oKMap[n] = r
                return 0
            } else {
                criticalMap[n] = r
                return 2
            }
        }

        if (r > critical) {
            criticalMap[n] = r
            return 2
        } else if (r > warning) {
            warningMap[n] = r
            return 1
        } else {
            oKMap[n] = r
            return 0
        }
        return 3
    }


    def attrListString

    if (attributes && attributes instanceof List) {
        attributes << testAttribute
        attrListString = attributes.unique().join(',')
    } else {
        attrListString = testAttribute
    }

    if (DEBUG)
        System.err.println "Attribute to retrieve: " + attrListString

    try {

    J4pClient client = J4pClient.url("http://${host}:${port}/jolokia")
                         .connectionTimeout(3000)
                         .socketTimeout(3000)
                         .build();

    //J4pReadRequest request = new J4pReadRequest("java.lang:type=Memory","HeapMemoryUsage");
    //J4pReadRequest request = new J4pReadRequest("${bean}", "${attrListString}");
   J4pSearchRequest request = new J4pSearchRequest(bean);
    //request.setPath("used");

    //J4pReadResponse response = client.execute(request);
     J4pSearchResponse response = client.execute(request);

            response.objectNames.each { ObjectName on ->
                       J4pReadRequest req = new J4pReadRequest(on,"${attrListString}")
                       Map requestParameters = [:]
                        requestParameters[J4pQueryParameter.IGNORE_ERRORS] = "true"
                       J4pReadResponse res = client.execute(req,requestParameters)

                       def responseValue =   res.getValue()

                       def objectNameAsList = on.toString().split(',')
                       def namesOnlyList = objectNameAsList.collect { it.split('=')[1] }
                       //def prettyPrintObjectName = namesOnlyList[0] + '.' + namesOnlyList[-1]
                       def prettyPrintObjectName = namesOnlyList.join('.')
                       def simpleObjectName = testAttribute
                       def nagiosName
                            if (objectName) {
                                nagiosName =  prettyPrintObjectName
                            } else {
                                nagiosName = simpleObjectName
                            }

                        if (responseValue instanceof Map) {
                                responseValue.each { k,v ->

                                    if (k.equals(testAttribute)) {
                                         processCheckResult(nagiosName,v)
                                    } else {
                                         outputAttributes["${nagiosName}.${k}"] = v
                                    }
                                }
                        } else {
                                processCheckResult(nagiosName,responseValue)
                        }
            }

            if (DEBUG) {
                System.err.println "OK States: " + oKMap
                System.err.println "WARNING States: " + warningMap
                System.err.println "CRITICAL States: " + criticalMap
            }

        /*
        response.each {
            //println client.execute(new J4pReadRequest(it,"")).getValue()
            println it
            J4pReadRequest req = new J4pReadRequest(it,'')
            J4pReadResponse res = client.execute(req)
            println res.getValue()
        }
        */

        if (criticalMap) {
            println "CRIT - " + criticalMap + ( outputAttributes ?: "")
            System.exit(2)
        } else if (warningMap) {
            println "WARN - " + warningMap  + ( outputAttributes ?: "")
            System.exit(1)
        } else {
            println "OK - " + oKMap + ( outputAttributes ?: "")
            System.exit(0)
        }


        }
     catch (J4pException j4pException) {
        println j4pException.message
        System.exit(1)
    } catch (InstanceNotFoundException instanceNotFoundException){
        println instanceNotFoundException.message
        System.exit(1)
    }  catch (Exception e) {
        println e.message
        System.exit(1)
    }

