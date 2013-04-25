package de.mobile.siteops.jolokia

import org.jolokia.client.J4pClient;
import org.jolokia.client.request.*
import org.jolokia.client.exception.J4pException;
import javax.management.InstanceNotFoundException;
import org.apache.commons.cli.Option

private final DEBUG = false
private final VERSION = 1.0

def cli = new CliBuilder(usage:'java -cp <path/jarfile-name.jar> de.mobile.siteops.jolokia.JolokiaReadClient parameters')
cli.with {
    cli.'?'(longOpt: 'help', 'usage information')
    h(longOpt: 'host', required: true, args: 1, 'remote host')
    p(longOpt: 'port', required: true, args: 1, 'remote port')
    b(longOpt: 'bean', required: true, args: 1, 'jmx bean name')
    a(longOpt: 'attribute', required: false, args: Option.UNLIMITED_VALUES, type: String, valueSeparator: ',', 'seperate multiple attributes with comma. (optional)')
    s(longOpt: 'subattribute', required: false, args: 1, type: String, 'path name for complex mbean attribute. (optional)')
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
    def attributes = opt.as
    def subattribute = opt.s
    def attrListString

    if (attributes)
        attrListString = attributes.join(',')
    else
        attrListString = ""

    try {

    J4pClient client = J4pClient.url("http://${host}:${port}/jolokia")
                         .connectionTimeout(3000)
                         .socketTimeout(3000)
                         .build();

    //J4pReadRequest request = new J4pReadRequest("java.lang:type=Memory","HeapMemoryUsage");
    J4pReadRequest request = new J4pReadRequest("${bean}", "${attrListString}");

     if (subattribute)
        request.setPath(subattribute);

    J4pReadResponse response = client.execute(request);

        def prefix
    if (response.getValue() instanceof Map)
        prefix = ""
    else
        prefix = attrListString + '='

    def responseValue = response.getValue()

    if (responseValue instanceof Map)
            responseValue.each { println prefix + it}
    else
             println prefix + responseValue

    } catch (J4pException j4pException) {
        println j4pException.message
        System.exit(1)
    } catch (InstanceNotFoundException instanceNotFoundException){
        println instanceNotFoundException.message
        System.exit(1)
    }  catch (Exception e) {
        println e.message
        System.exit(1)
    }

