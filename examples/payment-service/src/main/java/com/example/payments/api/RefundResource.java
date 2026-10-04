package com.example.payments.api;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/* Example class to test jax-rs */

@Path("/refunds")                          
public class RefundResource {

    @GET                                   
    @Path("/{refundId}")                   
    public void get(@PathParam("refundId") long refundId) { }

    @POST                                 
    public void create() { }

    @DELETE
    @Path("/{refundId}")
    public void cancel(@PathParam("refundId") long refundId) { }

    
}
