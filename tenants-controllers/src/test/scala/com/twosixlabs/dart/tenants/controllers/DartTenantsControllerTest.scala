package com.twosixlabs.dart.tenants.controllers

import com.twosixlabs.dart.auth.groups.ProgramManager
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{TenantAlreadyExistsException, TenantNotFoundException}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex, GlobalCorpus}
import com.twosixlabs.dart.auth.tenant.indices.InMemoryCorpusTenantIndex
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.rest.scalatra.models.FailureResponse
import com.twosixlabs.dart.tenants.api.m
import org.scalatra.test.scalatest._

import javax.servlet.http.HttpServletRequest
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DartTenantsControllerTest extends ScalatraFlatSpec {

    val index = new InMemoryCorpusTenantIndex()

    Await.result(
        index.addTenant( CorpusTenant( "test-tenant-1", GlobalCorpus ), CorpusTenant( "test-tenant-2", GlobalCorpus ), CorpusTenant( "test-tenant-3", GlobalCorpus ) ),
        10.seconds,
    )

    val dependencies = new DartTenantsController.Dependencies {
        override val corpusTenantIndex : CorpusTenantIndex = index
        override val serviceName : String = "tenants"
        override val secretKey : Option[String ] = None
        override val useDartAuth : Boolean = true
        override val basicAuthCredentials: Seq[ (String, String) ] = Seq.empty
    }

    val tenantsController = new DartTenantsController( dependencies ) {
        // Disable authentication / authorization by automatically returning user with max permissions
        override def authenticateUser( req : HttpServletRequest ) : DartUser = DartUser( "test", Set( ProgramManager ) )
    }

    addServlet( tenantsController, "/*" )

    behavior of "GET /"

    it should "return list of tenants" in {
        get( "/" ) {
            response.status shouldBe 200
            m.readValue( response.body, classOf[ List[ String ] ] ).toSet shouldBe Set( "test-tenant-1", "test-tenant-2", "test-tenant-3" )
        }
    }

    behavior of "POST /:tenantId"

    it should "add tenantId to index and return 201 when tenantId is valid and untaken" in {
        a[ TenantNotFoundException ] should be thrownBy Await.result( index.tenant( "test-tenant-4" ), 5.seconds )
        post( "/test-tenant-4" ) {
            println( response.body )
            response.status shouldBe 201
            response.body.isEmpty shouldBe true
            Await.result( index.tenant( "test-tenant-4" ), 5.second ) shouldBe CorpusTenant( "test-tenant-4", GlobalCorpus )
        }
    }

    it should "return 400 and an appropriate message if tenantId already exists" in {
        post( "/test-tenant-1" ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "already exists" )
        }
    }

    it should "return 400 and an appropriate message if tenantId is invalid" in {
        post( "/test.~tenant" ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "invalid" )
        }
    }

    it should "return 400 and an appropriate message if tenantId is global" in {
        post( "/global" ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "already exists" )
        }
    }

    behavior of "DELETE /:tenant"

    it should "delete a tenant and return 200 if tenant exists" in {
        Await.result( index.addDocumentsToTenant(  List( "doc-id-1", "doc-id-2", "doc-id-3" ), "test-tenant-4" ), 5.seconds )
        delete( "/test-tenant-4" ) {
            response.status shouldBe 200
            response.body.isEmpty shouldBe true
            a[ TenantNotFoundException ] should be thrownBy( Await.result( index.tenant( "test-tenant-4" ), 5.seconds ) )
            a[ TenantNotFoundException ] should be thrownBy( Await.result( index.tenantDocuments( "test-tenant-4" ), 5.seconds ) )
        }
    }

    it should "return 400 if tenantId is global" in {
        delete( "/global" ) {
            response.status shouldBe 400
            response.body should include( "cannot delete global corpus" )
        }
    }

    it should "return 404 if tenantId does not exist" in {
        delete( "/test-tenant-4" ) {
            response.status shouldBe 404
            response.body should include( "test-tenant-4" )
        }
    }

    behavior of "GET /:tenantId/documents"

    it should "return a list of documents when tenantId exists and has documents" in {
        Await.result( index.addDocumentsToTenant( List( "doc-id-1", "doc-id-2", "doc-id-3" ), "test-tenant-1" ), 5.seconds )
        get( "/test-tenant-1/documents" ) {
            response.status shouldBe 200
            m.readValue( response.body, classOf[ List[ String ] ] ).toSet shouldBe Set( "doc-id-1", "doc-id-2", "doc-id-3" )
        }
    }

    it should "return empty list of documents when tenantId exists but has not documents" in {
        get( "/test-tenant-2/documents" ) {
            response.status shouldBe 200
            m.readValue( response.body, classOf[ List[ String ] ] ).isEmpty shouldBe true
        }
    }

    it should "return 400 if tenantId is global" in {
        get( "/global/documents" ) {
            response.status shouldBe 400
            response.body should include( "cannot retrieve global document list" )
        }
    }

    behavior of "POST /:tenantId/documents"

    it should "return 200 if tenant exists and request includes valid query parameter" in {
        post( "/test-tenant-2/documents", Some( "docIds" -> "doc-id-1, doc-id-2,doc-id-3, doc-id-4 ,  doc-id-5" ) ) {
            response.status shouldBe 200
            Await.result( index.tenantDocuments( "test-tenant-2" ), 5.seconds ).toSet shouldBe Set( "doc-id-1", "doc-id-2", "doc-id-3", "doc-id-4", "doc-id-5" )
        }
    }

    it should "return 200 if tenant exists and request includes body of valid json list of doc ids" in {
        post( "/test-tenant-3/documents", """["doc-id-a","doc-id-b","doc-id-c","doc-id-d","doc-id-e"]""" ) {
            response.status shouldBe 200
            Await.result( index.tenantDocuments( "test-tenant-3" ), 5.seconds ).toSet shouldBe Set( "doc-id-a", "doc-id-b", "doc-id-c", "doc-id-d", "doc-id-e" )
        }
    }

    it should "return 200 if tenant exists and request includes body of valid json list of doc ids and valid docIds query parameter" in {
        submit( "POST", "/test-tenant-1/documents", Some( "docIds" -> "doc-id-4, doc-id-5" ), None, """["doc-id-a","doc-id-b","doc-id-c","doc-id-d","doc-id-e"]""" ) {
            response.status shouldBe 200
            Await.result( index.tenantDocuments( "test-tenant-1" ), 5.seconds ).toSet shouldBe Set( "doc-id-1", "doc-id-2", "doc-id-3", "doc-id-4", "doc-id-5", "doc-id-a", "doc-id-b", "doc-id-c", "doc-id-d", "doc-id-e" )
        }
    }

    it should "return 400 and appropriate message if tenantId exists but request includes neither query paramter nor json body" in {
        post( "/test-tenant-1/documents" ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "request is missing docIds query parameter and body" )
        }
    }

    it should "return 400 and appropriate message if tenantId is global" in {
        post( "/global/documents", Some( "docIds" -> "doc-id-1, doc-id-2,doc-id-3, doc-id-4 ,  doc-id-5" ) ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "all documents are already members of global tenant" )
        }
    }

    it should "return 400 and appropriate message if document is already in index" in {
        post( "/test-tenant-2/documents", Some( "docIds" -> "doc-id-2" ) ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "doc-id-2 is already in tenant test-tenant-2" )
        }
    }

    it should "return 404 and appropriate message if tenantId does not exist but request body/query is otherwise valid" in {
        submit( "POST", "/non-existent-tenant/documents", Some( "docIds" -> "doc-id-4, doc-id-5" ), None, """["doc-id-a","doc-id-b","doc-id-c","doc-id-d","doc-id-e"]""" ) {
            response.status shouldBe 404
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 404
            res.message should include( "non-existent-tenant" )
        }
    }

    behavior of "POST /:tenantId/documents/remove"

    it should "delete documents and return 200 if the tenant exists and the documents from query parameter are in it" in {
        post( "/test-tenant-1/documents/remove", Some( "docIds" -> "doc-id-4, doc-id-5" ) ) {
            response.status shouldBe 200
            response.body should have length 0
            Await.result( index.tenantDocuments( "test-tenant-1" ), 5.seconds ).toSet shouldBe Set( "doc-id-1", "doc-id-2", "doc-id-3", "doc-id-a", "doc-id-b", "doc-id-c", "doc-id-d", "doc-id-e" )
        }
    }

    it should "return 400 and an appropriate message if the tenant exists but docIds query parameter is missing" in {
        post( "/test-tenant-1/documents/remove" ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "request is missing docIds query parameter and body. Must include one or the other" )        }
    }

    it should "return 400 and appropriate message if tenantId is global" in {
        post( "/global/documents/remove", Some( "docIds" -> "doc-id-4, doc-id-5" ) ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "cannot remove document from global corpus" )
        }
    }

    it should "return 404 and if tenant does not exist" in {
        post( "/non-existent-tenant/documents/remove", Some( "docIds" -> "doc-id-4, doc-id-5" ) ) {
            response.status shouldBe 404
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 404
            res.message should include( "non-existent-tenant" )
        }
    }

    it should "return 404 and if doc id is not in tenant" in {
        post( "/test-tenant-1/documents/remove", Some( "docIds" -> "non-existent-doc-id" ) ) {
            response.status shouldBe 404
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 404
            res.message should include( "non-existent-doc-id" )
        }
    }

    behavior of "POST /:tenantId/clone/:newTenantId"

    it should "clone tenantId and return 201 if tenantId is valid tenant and newTenantId is valid tenant id" in {

        val docs = Await.result( index.tenantDocuments( "test-tenant-1" ), 5.seconds )
        docs.nonEmpty shouldBe true

        post( "/test-tenant-1/clone/new-test-tenant" ) {
            response.status shouldBe 201
            response.body.isEmpty shouldBe true

            val clonedIndex = Await.result( index.tenant( "new-test-tenant" ), 5.seconds )
            clonedIndex.id shouldBe "new-test-tenant"
            val clonedDocs = Await.result( index.tenantDocuments( "new-test-tenant" ), 5.seconds )
            clonedDocs.toSet shouldBe docs.toSet
        }
    }

    it should "return 404 if tenantId is non-existent" in {
        post( "/non-existent-tenant/clone/new-test-tenant" ) {
            response.status shouldBe 404
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 404
            res.message should include( "non-existent-tenant" )
        }
    }

    it should "return 400 if newTenantId already exists" in {
        post( "/test-tenant-1/clone/test-tenant-2" ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "test-tenant-2" )
        }
    }

    it should "return 400 if newTenantId is invalid" in {
        post( "/test-tenant-1/clone/invalid_tenant" ) {
            response.status shouldBe 400
            val res = m.readValue( response.body, classOf[ FailureResponse ] )
            res.status shouldBe 400
            res.message should include( "invalid_tenant" )
        }
    }

    override def header = null
}
