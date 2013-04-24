package de.mobile.siteops.jolokia

import org.jolokia.client.J4pClient;
import org.jolokia.client.request.*
import org.jolokia.client.exception.J4pException;
import javax.management.InstanceNotFoundException;
import org.apache.commons.cli.Option
import javax.management.ObjectName;

def cli = new CliBuilder(usage:'java -jar <jarfile-name.jar> parameters')
cli.with {
    cli.'?'(longOpt: 'help', 'usage information')
    h(longOpt: 'host', required: true, args: 1, 'remote host')
    p(longOpt: 'port', required: true, args: 1, 'remote port')
    b(longOpt: 'bean', required: true, args: 1, 'jmx bean name')
    a(longOpt: 'attribute', required: false, args: Option.UNLIMITED_VALUES, type: String, valueSeparator: ',', 'seperate multiple attributes with comma.')
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

                        if (responseValue instanceof Map)
                                responseValue.each { k,v ->  println k + "=" + v}
                        else
                                println attribute + "=" + responseValue
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

