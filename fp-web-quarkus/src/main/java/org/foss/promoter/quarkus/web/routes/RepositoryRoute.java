package org.foss.promoter.quarkus.web.routes;

import com.fasterxml.jackson.core.JacksonException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.foss.promoter.common.data.Repository;
import org.jboss.logging.Logger;

public class RepositoryRoute extends RouteBuilder {
    private static final Logger LOG = Logger.getLogger(RepositoryRoute.class);

    private void processRepository(Exchange exchange) {
        Repository body = exchange.getMessage().getBody(Repository.class);

        if (body == null || body.getName().isEmpty()) {
            exchange.getMessage().setHeader("valid", false);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setBody("Invalid request");

            return;
        }

        exchange.getMessage().setHeader("valid", true);

        if (body instanceof Repository) {
            LOG.infof("Received repository %s for processing", body.getName());
        }

        exchange.getMessage().setBody(body.getName());
    }

    @Override
    public void configure() throws Exception {
        restConfiguration().bindingMode(RestBindingMode.json);

        onException(JacksonException.class)
                .routeId("web-invalid-json")
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setBody().constant("Invalid json data");

        rest("/api")
                .get("/hello").to("direct:hello")
                .post("/repository").type(Repository.class).to("direct:repository");

        from("direct:hello")
                .routeId("web-hello")
                .transform().constant("Hello World");

        from("direct:repository")
                .routeId("web-repository")
                .process(this::processRepository)
                .choice()
                    .when(header("valid").isEqualTo(true))
                        .toF("kafka:repositories")
                .endChoice();
    }
}
