package org.nttdata.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.nttdata.entities.Customer;
import org.nttdata.entities.Product;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;
import static javax.ws.rs.core.Response.Status.*;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Slf4j
@ApplicationScoped
public class CustomerRepository  implements PanacheRepositoryBase<Customer, Long> {

    @Inject
    Vertx vertx;

    @Inject
            @GraphQLClient("product-dynamic-client")
    DynamicGraphQLClient dynamicGraphQLClient;

    private WebClient webClient;

    @PostConstruct
    void initialize(){
        this.webClient = WebClient.create(vertx,
                new WebClientOptions().setDefaultHost("localhost")
                        .setDefaultPort(8081).setSsl(false).setTrustAll(true));
    }

    public Uni<Customer> getByIdProduct(Long Id) {
        return Uni.combine().all().unis(getCustomerReactive(Id),getAllProducts())
                .combinedWith((v1,v2) -> {
                    v1.getProducts().forEach(product -> {
                        v2.forEach(p -> {
                            log.info("Ids are: " + product.getProduct() +" = " +p.getId());
                            if(product.getProduct() == p.getId()){
                                product.setName(p.getName());
                                product.setDescription(p.getDescription());
                            }
                        });
                    });
                    return v1;
                });
    }

    private Uni<Customer> getCustomerReactive(Long Id){
        return Customer.findById(Id);
    }

    private Uni<List<Product>> getAllProducts(){
        return webClient.get(8081, "localhost", "/product").send()
                .onFailure().invoke(res -> log.error("Error recuperando productos " + res))
                .onItem().transform(res -> {
                    List<Product> lista = new ArrayList<>();
                    JsonArray objects = res.bodyAsJsonArray();
                    objects.forEach(p -> {
                        log.info("See Objects " + objects);
                        ObjectMapper objectMapper = new ObjectMapper();

                        Product product = null;
                        try{
                            product = objectMapper.readValue(p.toString(), Product.class);
                        } catch (JsonProcessingException e){
                            e.printStackTrace();
                        }
                        lista.add(product);
                    });
                    return lista;
                });
    }

    public Uni<Response> add(Customer c) {
        return Panache.withTransaction(c::persist)
                .replaceWith(Response.ok(c).status(CREATED)::build);
    }

    public Uni<Response> delete(Long Id) {
        return Panache.withTransaction(() -> Customer.deleteById(Id))
                .map(deleted -> deleted
                        ? Response.ok().status(NO_CONTENT).build()
                        : Response.ok().status(NOT_FOUND).build());
    }

    public Uni<Response> update(Long id, Customer c) {
        if (c == null || c.getCode() == null) {
            throw new WebApplicationException("Product code was not set on request.", HttpResponseStatus.UNPROCESSABLE_ENTITY.code());
        }
        return Panache
                .withTransaction(() -> Customer.<Customer> findById(id)
                        .onItem().ifNotNull().invoke(entity -> {
                            entity.setNames(c.getNames());
                            entity.setAccountNumber(c.getAccountNumber());
                            entity.setCode(c.getCode());
                        })
                )
                .onItem().ifNotNull().transform(entity -> Response.ok(entity).build())
                .onItem().ifNull().continueWith(Response.ok().status(NOT_FOUND)::build);
    }



    public Uni<Customer> addMutation(Customer customer) {
        customer.getProducts().forEach(p -> p.setCustomer(customer));
        return Panache.withTransaction(customer::persist)
                .replaceWith(customer);
    }

    public Uni<Boolean> deleteMutation (Long id) {
        return Panache.withTransaction(() -> Customer.deleteById(id));
    }

    public Uni<List<Product>> getProductsGraphQl() throws Exception{
        Document query = Document.document(
                operation(
                        field("allProducts",
                                field("id"),
                                field("code"),
                                field("name"),
                                field("description"))
                )
        );

        Uni<io.smallrye.graphql.client.Response> responseUni = dynamicGraphQLClient.executeAsync(query);

        return responseUni.map(r -> r.getList(Product.class, "allProducts"));
    }

    public Uni<Product> getByIdProductGraphQl(Long id) throws Exception{
        Document query = Document.document(
                operation(
                        field("product(productId:"+id+")",
                                field("id"),
                                field("code"),
                                field("name"),
                                field("description"))
                )
        );

        Uni<io.smallrye.graphql.client.Response> responseUni = dynamicGraphQLClient.executeAsync(query);

        System.out.println(responseUni.onItem().ifNotNull().transform(r -> r.getObject(Product.class, "product"))
                .onItem());
        return responseUni.onItem().ifNotNull().transform(r -> r.getObject(Product.class, "product"));
    }
}
