package de.mobile.siteops.jolokia

import org.jolokia.client.J4pClient;
import org.jolokia.client.request.*
import org.jolokia.client.exception.J4pException;
import javax.management.InstanceNotFoundException;
import org.apache.commons.cli.Option;

private final DEBUG = false
private final VERSION = 1.0

def cli = new CliBuilder(usage:'java -cp <path/jarfile-name.jar> de.mobile.siteops.jolokia.JolokiaCompareNagiosClient parameters')
cli.with {
    cli.'?'(longOpt: 'help', 'usage information')
    h(longOpt: 'host', required: true, args: 1, 'remote host')
    p(longOpt: 'port', required: true, args: 1, 'remote port')
    b(longOpt: 'bean', required: true, args: 1, 'jmx bean or pattern')
    //a(longOpt: 'attribute', required: true, args: Option.UNLIMITED_VALUES, type: String, valueSeparator: ',', 'separate multiple attributes with comma. First attribute will be used to evaluate nagios thresholds.')
    w(longOpt: 'warn', required: true, args: 1, type: String, 'WARN threshold in PERCENT')
    c(longOpt: 'critical', required: true, args: 1, type: String, 'CRITICAL threshold in PERCENT')
    x(longOpt: 'maxattribute', required: true, args: 1, 'the attribute that specifies the max possible value')
    s(longOpt: 'stateattribute', required: true, args: 1, 'the attribute that specifies the current state')
    o(longOpt: 'subattribute', required: false, args: 1, type: String, 'path name for complex mbean attribute (optional)')
    v(longOpt: 'version', required: false, 'version information (optional)')
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
    def warning = opt.w
    def critical = opt.c
    def state = opt.s
    def maximum = opt.x
    def subattribute = opt.o
    def version = opt.v

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

    try {

    J4pClient client = J4pClient.url("http://${host}:${port}/jolokia")
                         .connectionTimeout(3000)
                         .socketTimeout(3000)
                         .build();

    String attrListString = "${state},${maximum}"

    J4pSearchRequest request = new J4pSearchRequest(bean);
    J4pSearchResponse response = client.execute(request);

    def objectList = response.objectNames

    if (objectList.size() > 1) {
            println "more than one mbean object was found. Make the --bean pattern more specific"
            objectList.each { System.err.println "--> " + it }
            System.exit(2)
    }

    def objectName = objectList.pop()

    J4pReadRequest req = new J4pReadRequest(objectName,"${attrListString}")

    if (subattribute)
        req.setPath(subattribute);

    Map requestParameters = [:]
    requestParameters[J4pQueryParameter.IGNORE_ERRORS] = "true"
                       J4pReadResponse res = client.execute(req,requestParameters)

                       def responseValueMap = res.getValue()

                       if (!responseValueMap instanceof Map) {
                           println "ERROR: required attributes could not be found: ${responseValueMap}"
                           System.exit(2)
                       }

                       def currentState = responseValueMap[state]
                       def maximumValue = responseValueMap[maximum]
                        if (maximumValue == 0) {
                            println "Maximum possible value of '${maximum}' is 0.  This check does not make sense."
                            System.exit(3)
                        }

                       def percentOfMax = new Double((currentState/maximumValue) * 100).round(2)

                        if (DEBUG) {
                            println "${state} CURRENT: " + currentState
                            println "${maximum} MAX: " + maximumValue
                            println "${state}/${maximum} PCT MAX: " + percentOfMax
                        }

        if(percentOfMax>critical) {
             println "CRITICAL: '${state}' value ${currentState} exceeds ${critical}% of '${maximum}' ${maximumValue}"
             System.exit(2)
        } else if (percentOfMax>warning) {
             println "WARN: '${state}' value ${currentState} exceeds ${warning}% of '${maximum}' ${maximumValue}"
             System.exit(1)
        } else {
             println "OK: '${state}' value ${currentState} is ${percentOfMax}% of '${maximum}' ${maximumValue}"
             System.exit(0)
        }


    }  catch (J4pException j4pException) {
        println "ERROR: " +  j4pException.message
        System.exit(2)
    } catch (InstanceNotFoundException instanceNotFoundException){
        println "ERROR: " +  instanceNotFoundException.message
        System.exit(2)
    }  catch (Exception e) {
        println "ERROR: " +  e.message
        System.exit(2)
    }

