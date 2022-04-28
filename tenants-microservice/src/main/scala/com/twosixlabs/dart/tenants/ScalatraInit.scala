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
import scala.util.{ Failure, Success, Try }
import scala.util.matching.Regex

class ScalatraInit extends LifeCycle {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    private val config : Config = ConfigFactory.defaultApplication().resolve()

    val authDependencies : SecureDartController.AuthDependencies = SecureDartController.authDeps( config )

    val allowedOrigins = Try( config.getString( "cors.allowed.origins" ) ).getOrElse( "*" )

    val InMemoryPattern : Regex = """^in[-_]*memory$""".r

    def getIndex( i : String ) : Option[ CorpusTenantIndex ] = {
        Try( config.getString( s"index.$i" ) )
          .toOption
          .map( _.trim.toLowerCase ) flatMap {
            case "arango" =>
                Some( ArangoCorpusTenantIndex( config ) )
            case "elasticsearch" =>
                Some( ElasticsearchCorpusTenantIndex( config ) )
            case "keycloak" =>
                Some( KeycloakCorpusTenantIndex( config ) )
            case "test" => Some( new InMemoryCorpusTenantIndex() )
            case InMemoryPattern() => Some( new InMemoryCorpusTenantIndex() )
            case _ => None
        }
    }

    val tenantIndex : CorpusTenantIndex = {
        val master : CorpusTenantIndex = getIndex( "master" ).getOrElse( throw new IllegalStateException( "Must provide valid master index (INDEX_MASTER)" ) )
        val indices = ( 1 to 10 ).flatMap( i =>  getIndex( i.toString ) )
        println( ( master +: indices ).mkString( "TENANT INDICES\n==============\n", "\n", "\n" ) )
        new ParallelCorpusTenantIndex( master, indices : _* )
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
