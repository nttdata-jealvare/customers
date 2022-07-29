package org.nttdata.api;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestPath;
import org.nttdata.entities.Customer;
import org.nttdata.entities.Product;
import org.nttdata.repositories.CustomerRepository;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Slf4j
@Path("/customer")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerApi {

    @Inject
    CustomerRepository cr;

    @GET
    public Uni<List<PanacheEntityBase>> list() {
        return Customer.listAll(Sort.by("names"));
    }

    @GET
    @Path("using-repository")
    public Uni<List<Customer>> listUsingRepository() {
        return cr.findAll().list();
    }

    @GET
    @Path("/{Id}")
    public Uni<PanacheEntityBase> getById(@PathParam("Id") Long Id) {
        return Customer.findById(Id);
    }

    @GET
    @Path("/{Id}/product")
    public Uni<Customer> getByIdProduct(@PathParam("Id") Long id){
        return cr.getByIdProduct(id);
    }

    @POST
    public Uni<Response> add(Customer c) {
        return cr.add(c);
    }

    @DELETE
    @Path("/{Id}")
    public Uni<Response> delete(@PathParam("Id") Long id) {
        return cr.delete(id);
    }
    @PUT
    @Path("{id}")
    public Uni<Response> update(@RestPath Long id, Customer c) {
        return cr.update(id, c);
    }

    /**
     *
     */
    @GET
    @Path("/products-graphql")
    public Uni<List<Product>> getProductsGraphQl() throws Exception{
        return cr.getProductsGraphQl();
    }

    @GET
    @Path("/{Id}/products-graphql")
    public Uni<Product> getByIdProductsGraphQl(@PathParam("Id") Long id) throws Exception{
        return cr.getByIdProductGraphQl(id);
    }
}