package com.twosixlabs.dart.tenants

import com.twosixlabs.dart.auth.controllers.SecureDartController
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex
import com.twosixlabs.dart.auth.tenant.indices.{ ArangoCorpusTenantIndex, InMemoryCorpusTenantIndex, KeycloakCorpusTenantIndex, ParallelCorpusTenantIndex }
import com.twosixlabs.dart.rest.ApiStandards
import com.twosixlabs.dart.rest.scalatra.DartRootServlet
import com.twosixlabs.dart.search.ElasticsearchCorpusTenantIndex
import com.twosixlabs.dart.tenants.controllers.DartTenantsController
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatra.LifeCycle
import org.slf4j.{ Logger, LoggerFactory }

import javax.servlet.ServletContext
import scala.util.Try

class ScalatraInit extends LifeCycle {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    private val config : Config = ConfigFactory.defaultApplication().resolve()

    val authDependencies : SecureDartController.AuthDependencies = SecureDartController.authDeps( config )

    val allowedOrigins = Try( config.getString( "cors.allowed.origins" ) ).getOrElse( "*" )

    val testMode = Try( config.getBoolean( "tenants.test.mode" ) )
      .getOrElse( config.getString( "tenants.test.mode" ).toLowerCase.trim == "true" )

    val tenantIndex : CorpusTenantIndex =
        if ( testMode ) new InMemoryCorpusTenantIndex()
        else {
            val keycloakTenantIndex : KeycloakCorpusTenantIndex = KeycloakCorpusTenantIndex( config )
            val arangoTenantIndex : ArangoCorpusTenantIndex = ArangoCorpusTenantIndex( config )
            val elasticsearchTenantIndex : ElasticsearchCorpusTenantIndex = ElasticsearchCorpusTenantIndex( config )
            new ParallelCorpusTenantIndex(
                arangoTenantIndex,
                keycloakTenantIndex,
                elasticsearchTenantIndex,
            )
        }

    val tenantsController : DartTenantsController = {
        DartTenantsController( tenantIndex, authDependencies )
    }

    val basePath : String = ApiStandards.DART_API_PREFIX_V1 + "/tenants"

    val rootController = new DartRootServlet(
        Some( basePath ),
        Some( getClass.getPackage.getImplementationVersion )
    )

    // Initialize scalatra: mounts servlets
    override def init( context : ServletContext ) : Unit = {
        context.setInitParameter( "org.scalatra.cors.allowedOrigins", allowedOrigins )
        context.mount( rootController, "/*" )
        context.mount( tenantsController, basePath + "/*" )
    }

    // Scalatra callback to close out resources
    override def destroy( context : ServletContext ) : Unit = {
        super.destroy( context )
    }

}
