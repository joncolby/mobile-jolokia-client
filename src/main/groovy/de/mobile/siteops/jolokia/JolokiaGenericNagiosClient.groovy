package de.mobile.siteops.jolokia

import org.jolokia.client.J4pClient;
import org.jolokia.client.request.*
import org.jolokia.client.exception.J4pException;
import javax.management.InstanceNotFoundException;
import org.apache.commons.cli.Option;

private final DEBUG = false
private final VERSION = 1.0

def cli = new CliBuilder(usage:'java -jar <path/jarfile-name.jar> parameters')
cli.with {
    cli.'?'(longOpt: 'help', 'usage information')
    h(longOpt: 'host', required: true, args: 1, 'remote host')
    p(longOpt: 'port', required: true, args: 1, 'remote port')
    b(longOpt: 'bean', required: true, args: 1, 'jmx bean name')
    a(longOpt: 'attribute', required: true, args: Option.UNLIMITED_VALUES, type: String, valueSeparator: ',', 'seperate multiple attributes with comma. First attribute will be used to evaluate nagios thresholds.')
    w(longOpt: 'warn', required: false, args: 1, type: String, 'WARN threshold (optional)')
    c(longOpt: 'critical', required: false, args: 1, type: String, 'CRITICAL threshold (optional)')
    m(longOpt: 'match', required: false, args: 1, type: String, 'result must match this string or script will exit with CRITICAL status. (optional)')
    s(longOpt: 'subattribute', required: false, args: Option.UNLIMITED_VALUES, type: String, valueSeparator: ',', 'path names for complex attribute values. (optional)')
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
    def attribute = opt.as
    def thresholdTestAttribute = opt.a
    def warning = opt.w
    def critical = opt.c
    def subattribute = opt.s

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

    try {

    J4pClient client = J4pClient.url("http://${host}:${port}/jolokia")
                         .connectionTimeout(3000)
                         .socketTimeout(3000)
                         .build();
     String attrListString = attribute.join(',')

    //J4pReadRequest request = new J4pReadRequest("java.lang:type=Memory","HeapMemoryUsage");
    J4pReadRequest request = new J4pReadRequest("${bean}", "${attrListString}");

    if (subattribute)
        request.setPath(subattribute);

     J4pReadResponse response = client.execute(request);
     String firstAttributeResponseValue

     def isMap = false
     if (response.getValue() instanceof Map ) {
         isMap = true
         firstAttributeResponseValue = response.getValue()."${thresholdTestAttribute}"
     } else {
         firstAttributeResponseValue = response.getValue()
     }

     if (match) {
         if (firstAttributeResponseValue.equalsIgnoreCase(match)) {
             println "OK: value of attribute ${thresholdTestAttribute} eq '${match}'"
             System.exit(0)
         } else {
             println "CRITICAL: value of attribute ${thresholdTestAttribute} '${firstAttributeResponseValue}' not eq '${match}'"
             System.exit(2)
         }
     }

        Long testAttributeAsNumber
        try {
            testAttributeAsNumber = Double.valueOf(firstAttributeResponseValue)
        } catch (Exception e) {
            println "ERROR: Value of '${thresholdTestAttribute}' (${firstAttributeResponseValue}) does not appear to be a number. Maybe the 'match' parameter is what you need?"
            cli.usage()
            System.exit(2)
        }

        if (testAttributeAsNumber>critical) {
            println "CRITICAL: ${thresholdTestAttribute} value ${testAttributeAsNumber} exceeds ${critical}"
            System.exit(2)
        } else if (testAttributeAsNumber > warning) {
            println "WARN: ${thresholdTestAttribute} value ${testAttributeAsNumber} exceeds ${warning}"
            System.exit(1)
        } else {
            println "OK: ${thresholdTestAttribute} value ${testAttributeAsNumber} " +  (isMap ? response.getValue() : "")
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

