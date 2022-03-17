package com.twosixlabs.dart.tenants.api

import com.twosixlabs.dart.rest.scalatra.models.FailureResponse
import org.json4s.{Formats, NoTypeHints, Serialization}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.json4s._

object DartTenantsApiSpec extends DartServiceApiDefinition {

    override val serviceName : String = "Dart Tenants"

    override val servicePathName : Option[ String ] = Some( "tenants" )

    implicit val serialization: Serialization = org.json4s.jackson.Serialization
    implicit val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)

    lazy val getTenants = endpoint
      .description( "List all tenants" )
      .get
      .out( jsonBody[ List[ String ] ].description( "Successfully retrieved tenants" ) )
      .addToDart()

    lazy val addTenant = endpoint
      .description( "Add new tenant" )
      .post
      .in( path[ String ]( "tenantId" ) )
      .out( statusCode( StatusCode.Created ).description( "Succesfully created tenant" ) )
      .addToDart( badRequestErr( "Tenant id is invalid format or already exists" ) )

    lazy val removeTenant = endpoint
      .description( "Remove existing tenant" )
      .delete
      .in( path[ String ]( "tenantId" ) )
      .out( statusCode( StatusCode.Ok ).description( "Succesfully deleted tenant" ) )
      .addToDart( notFoundErr( "Tenant does not exist" ) )

    lazy val getDocumentsFromTenant = endpoint
      .description( "List documents in a tenant corpus" )
      .get
      .in( path[ String ]( "tenantId" ) / "documents" )
      .out( jsonBody[ List[ String ] ].description( "Successfully retrieved list of docIds in tenant corpus" ) )
      .addToDart( notFoundErr( "Tenant does not exist" ) )

    lazy val addDocumentsToTenant = endpoint
      .description( "Add documents to a tenant corpus" )
      .post
      .in( path[ String ]( "tenantId" ) / "documents" )
      .in( query[ Option[ String ] ]( "docIds" ).description( "Comma-separated list of docIds to add to tenant corpus" ) )
      .in( jsonBody[ Option[ List[ String ] ] ].description( "Json list of docIds to add to tenant corpus" ) )
      .out( statusCode( StatusCode.Ok ).description( "Succesfully added documents to tenant" ) )
      .addToDart( notFoundErr( "Tenant does not exist" ), badRequestErr( "Missing or invalid docIds list (whether query parameter or json request body)" ) )

    lazy val removeDocumentsFromTenant = endpoint
      .description( "Remove documents from a tenant corpus" )
      .post
      .in( path[ String ]( "tenantId" ) / "documents" / "remove" )
      .in( query[ String ]( "docIds" ).description( "Comma-separated list of docIds to remove from tenant corpus" ) )
      .in( jsonBody[ Option[ List[ String ] ] ].description( "Json list of docIds to remove from tenant corpus" ) )
      .out( statusCode( StatusCode.Ok ).description( "Succesfully removed documents from tenant" ) )
      .addToDart( notFoundErr( "Tenant does not exist" ), badRequestErr( "Missing or invalid docIds list (whether query parameter or json request body)" ) )

}
