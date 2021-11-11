package integration.demo;

import com.asana.Client;
import com.asana.models.Task;
import io.quarkus.arc.Unremovable;
import org.apache.camel.Exchange;
import org.apache.camel.jsonpath.JsonPath;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Unremovable
@Named("asanaBean")
public class AsanaBean  {

    @ConfigProperty(name = "asana.pat")
     String asanaToken;

    @ConfigProperty(name = "asana.workspace.gid")
     String workspaceGid;

    @ConfigProperty(name = "asana.project.gid")
     String projectGid;

    private Client client;

    private static final Logger LOG = Logger.getLogger(AsanaBean.class);

    @PostConstruct
    void init(){
         client = Client.accessToken(asanaToken);
    }

    public void parseToAsana(@JsonPath("$.opportunity") Map opp, @JsonPath("$.account") Map account, @JsonPath("$.lineItems") List lineItems , Exchange exchange) throws Exception {

       Map oppNew =  (Map) ((List) opp.get("new")).get(0);

       if(opp != null && opp.get("old") != null && ((List) opp.get("old")).size() > 0 ){

           updateTask(exchange,createNotes(oppNew,account,lineItems),oppNew);

       }else{
           createTask(exchange,createNotes(oppNew,account,lineItems),oppNew);
       }

    }

    private void updateTask(Exchange exchange,  String notes, Map opp)  throws Exception {

        Task demoTask = client.tasks.updateTask("external:" + opp.get("Id").toString())
                .data("name", opp.get("Name") )
                .data("html_notes", notes)
                .execute();

        LOG.info("Task Updated: " + demoTask.gid );

        exchange.getIn().setBody("{ \"taskGid\" : \""+ demoTask.gid + "\" } ");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
    }

    private void createTask(Exchange exchange, String notes, Map<String,Object> opp) throws Exception{

        Map<String, Object> external = new HashMap<>();
        external.put("gid",opp.get("Id"));
        external.put("data","opportunityId");

        Task demoTask = client.tasks.createInWorkspace(workspaceGid)
                .data("name", opp.get("Name") )
                .data("projects", Arrays.asList(projectGid))
                .data("html_notes", notes)
                .data("external", external)
                .execute();

        LOG.info("Task created: " + demoTask.gid );

        exchange.getIn().setBody("{ \"taskGid\" : \""+ demoTask.gid + "\" } ");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
    }

    private String createNotes(Map opp, Map account, List lineItems){
        StringBuilder builder = new StringBuilder();

        builder.append("<body>\n");
        builder.append("<strong>Account Name: </strong>").append(account.get("Name")).append("\n");
        builder.append("<strong>Account Number: </strong>").append(account.get("AccountNumber")).append("\n");
        builder.append("<strong>Deal Size: </strong>").append(opp.get("Amount")).append("\n");
        builder.append("<strong>Products Involved: </strong>").append("\n");

        if(lineItems != null && lineItems.size() > 0){

            builder.append("<ul>\n");

            for (Object lineItem : lineItems) {
                Map line = (Map) lineItem;
                builder.append("<li>").append(line.get("Quantity")).append("x ").append(line.get("Name")).append("</li>\n");
            }

            builder.append("</ul>\n");
        }

        builder.append("</body>");

        return  builder.toString();
    }
}
