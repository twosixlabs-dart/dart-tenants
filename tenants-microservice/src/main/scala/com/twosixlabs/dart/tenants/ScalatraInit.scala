package com.twosixlabs.dart.tenants

import com.twosixlabs.dart.auth.controllers.SecureDartController
import com.twosixlabs.dart.auth.tenant.indices.{ArangoCorpusTenantIndex, KeycloakCorpusTenantIndex, ParallelCorpusTenantIndex}
import com.twosixlabs.dart.rest.ApiStandards
import com.twosixlabs.dart.rest.scalatra.DartRootServlet
import com.twosixlabs.dart.search.ElasticsearchCorpusTenantIndex
import com.twosixlabs.dart.tenants.controllers.DartTenantsController
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatra.LifeCycle
import org.slf4j.{Logger, LoggerFactory}

import javax.servlet.ServletContext

class ScalatraInit extends LifeCycle {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    private val config : Config = ConfigFactory.defaultApplication().resolve()

    val authDependencies : SecureDartController.AuthDependencies = SecureDartController.authDeps( config )

    val keycloakTenantIndex : KeycloakCorpusTenantIndex = KeycloakCorpusTenantIndex( config )
    val arangoTenantIndex : ArangoCorpusTenantIndex = ArangoCorpusTenantIndex( config )
    val elasticsearchTenantIndex : ElasticsearchCorpusTenantIndex = ElasticsearchCorpusTenantIndex( config )

    val tenantIndex : ParallelCorpusTenantIndex = new ParallelCorpusTenantIndex(
        arangoTenantIndex,
        keycloakTenantIndex,
        elasticsearchTenantIndex,
        )

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
//        context.initParameters( "org.scalatra.cors.allowedOrigins" ) = allowedOrigins
        context.mount( rootController, "/*" )
        context.mount( tenantsController, basePath + "/*" )
    }

    // Scalatra callback to close out resources
    override def destroy( context : ServletContext ) : Unit = {
        super.destroy( context )
    }

}
