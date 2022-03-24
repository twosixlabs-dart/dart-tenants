package com.twosixlabs.dart.tenants.controllers

import com.twosixlabs.dart.auth.controllers.SecureDartController
import com.twosixlabs.dart.auth.controllers.SecureDartController.AuthDependencies
import com.twosixlabs.dart.auth.permissions.DartOperations.{ CreateTenant, RetrieveTenant, SearchCorpus, TenantOperation, ViewTenant }
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{ DocIdAlreadyInTenantException, DocIdMissingFromTenantException, InvalidTenantIdException, NonAtomicTenantIndexFailureException, TenantAlreadyExistsException, TenantNotFoundException }
import com.twosixlabs.dart.auth.tenant.{ CorpusTenantIndex, DartTenant, GlobalCorpus }
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.exceptions.ExceptionImplicits.FutureExceptionLogging
import com.twosixlabs.dart.exceptions.{ AuthorizationException, BadQueryParameterException, BadRequestBodyException, ResourceNotFoundException }
import com.twosixlabs.dart.rest.scalatra.AsyncDartScalatraServlet
import com.twosixlabs.dart.utils.JsonHelper.unmarshal
import org.hungerford.rbac.{ PermissibleSet, PermissionSource }
import org.scalatra.{ Created, MethodOverride, Ok }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object DartTenantsController {
    trait Dependencies extends SecureDartController.Dependencies {
        val corpusTenantIndex : CorpusTenantIndex

        def buildTenantsController : DartTenantsController = new DartTenantsController( this )
    }

    case class Deps(
        override val corpusTenantIndex : CorpusTenantIndex,
        secureControllerDeps : SecureDartController.Dependencies,
    ) extends Dependencies {
        override val serviceName : String = secureControllerDeps.serviceName
        override val secretKey : Option[String ] = secureControllerDeps.secretKey
        override val useDartAuth: Boolean = secureControllerDeps.useDartAuth
        override val basicAuthCredentials: Seq[ (String, String) ] = secureControllerDeps.basicAuthCredentials
    }

    def apply(
        corpusTenantIndex: CorpusTenantIndex,
        bypassAuth : Boolean = false,
        secretKey : Option[ String ],
        basicAuthCreds : Seq[ (String, String) ] = Seq.empty[ (String, String) ],
    ) : DartTenantsController = Deps(
        corpusTenantIndex,
        SecureDartController.deps( "tenants", secretKey, !bypassAuth, basicAuthCreds )
    ).buildTenantsController

    def apply(
        corpusTenantIndex: CorpusTenantIndex,
        authDependencies: AuthDependencies,
    ) : DartTenantsController = Deps(
        corpusTenantIndex,
        SecureDartController.deps( "tenants", authDependencies )
    ).buildTenantsController
}

class DartTenantsController( dependencies : DartTenantsController.Dependencies )
  extends AsyncDartScalatraServlet with SecureDartController {

    override val serviceName : String = dependencies.serviceName
    override val secretKey : Option[ String ] = dependencies.secretKey
    override val useDartAuth: Boolean = dependencies.useDartAuth
    override val basicAuthCredentials: Seq[ (String, String) ] = dependencies.basicAuthCredentials

    import dependencies.corpusTenantIndex

    setStandardConfig()

    override protected implicit def executor : ExecutionContext = corpusTenantIndex.executionContext

    get( "/" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        corpusTenantIndex.allTenants.map { tenants =>
            val permittedTenants = tenants.filter( v => {
                v.parent == GlobalCorpus &&
                  user.roles.permissions.permits( ViewTenant( v ) )
            } ).map( _.id )
            if ( user.roles.permissions.permits( ViewTenant( GlobalCorpus ) ) )
                DartTenant.globalId +: permittedTenants
            else permittedTenants
        }
    } ) )

    post( "/:tenantId" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        CreateTenant.in( GlobalCorpus ).secureDart {
            val tenantId = params.get( "tenantId" ).map( _.trim.toLowerCase ) match {
                case None => throw new BadQueryParameterException( "parameter tenantId is missing" )
                case Some( "global" ) => throw new BadQueryParameterException( "tenant already exists with id global" )
                case Some( res ) => res.toLowerCase
            }

            corpusTenantIndex.addTenant( tenantId ).map( _ => {
                LOG.info( "success!!" )
                Created()
            } ) recoverWith {
                case _ : TenantAlreadyExistsException => throw new BadQueryParameterException( s"tenant already exists with id $tenantId" )
                case e : InvalidTenantIdException => throw new BadQueryParameterException( "tenantId", Some( tenantId ), e.getMessage )
                case e => Future.failed( e )
            } logged
        }
    } ) )

    delete( "/:tenantId" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        CreateTenant.in( GlobalCorpus ).secureDart {
            val tenantId = params.get( "tenantId" ).map( _.trim.toLowerCase ) match {
                case None => throw new BadQueryParameterException( "parameter tenantId is missing" )
                case Some( "global" ) => throw new BadQueryParameterException( "cannot delete global corpus" )
                case Some( res ) => res.toLowerCase
            }

            corpusTenantIndex.tenant( tenantId ).recoverWith {
                case _ => Future.failed( new ResourceNotFoundException( "tenant", Some( tenantId ) ) )
            } flatMap ( existingTenant => corpusTenantIndex.removeTenant( existingTenant ) ) map { _ =>
                Ok()
            } recoverWith {
                case e : TenantNotFoundException => throw new ResourceNotFoundException( "tenantId", Some( tenantId ) )
                case e => Future.failed( e )
            } logged
        }
    } ) )

    get( "/:tenantId/documents" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        RetrieveTenant.from( GlobalCorpus ).secureDart {
            val tenantId = params.get( "tenantId" ).map( _.trim.toLowerCase ) match {
                case None => throw new BadQueryParameterException( "parameter tenantId is missing" )
                case Some( "global" ) => throw new BadQueryParameterException( "cannot retrieve global document list; use CorpEx" )
                case Some( res ) => res.toLowerCase
            }

            corpusTenantIndex.tenant( tenantId ).recoverWith {
                case _ => Future.failed( new ResourceNotFoundException( "tenant", Some( tenantId ) ) )
            } flatMap ( existingTenant => corpusTenantIndex.tenantDocuments( existingTenant ) ) recoverWith {
                case e : TenantNotFoundException => throw new ResourceNotFoundException( "tenantId", Some( tenantId ) )
                case e => Future.failed( e )
            } logged
        }
    } ) )

    post( "/:tenantId/documents" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        CreateTenant.in( GlobalCorpus ).secureDart {
            val tenantId = params.get( "tenantId" ).map( _.trim.toLowerCase ) match {
                case None => throw new BadQueryParameterException( "parameter tenantId is missing" )
                case Some( "global" ) => throw new BadQueryParameterException( "all documents are already members of global tenant" )
                case Some( res ) => res.toLowerCase
            }

            val queryDocIds : List[ String ] = params.get( "docIds" )
              .map( _.split( "," ).map( _.trim ).toList ).toList.flatten

            val bodyDocIds : List[ String ] = request.body match {
                case "" => Nil
                case json => unmarshal( json, classOf[ List[ String ] ] ).getOrElse( throw new BadRequestBodyException( "Request body is not valid JSON list of strings" ) )
            }

            val docIds = queryDocIds ++ bodyDocIds

            if ( docIds.isEmpty ) throw new BadRequestBodyException( "request is missing docIds query parameter and body. Must include one or the other." )

            corpusTenantIndex.tenant( tenantId ).recoverWith {
                case _ => Future.failed( new ResourceNotFoundException( "tenant", Some( tenantId ) ) )
            } flatMap ( existingTenant => corpusTenantIndex.addDocumentsToTenant( docIds, existingTenant.id )  ) map { _ =>
                Ok()
            } recoverWith {
                case e : TenantNotFoundException => throw new ResourceNotFoundException( "tenantId", Some( tenantId ) )
                case e : DocIdAlreadyInTenantException => throw new BadQueryParameterException( s"Document ${e.docId} is already in tenant $tenantId" )
                case e => Future.failed( e )
            } logged
        }
    } ) )

    post( "/:tenantId/documents/remove" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        CreateTenant.in( GlobalCorpus ).secureDart {
            val tenantId = params.get( "tenantId" ).map( _.trim.toLowerCase ) match {
                case None => throw new BadQueryParameterException( "parameter tenantId is missing" )
                case Some( "global" ) => throw new BadQueryParameterException( "cannot remove document from global corpus" )
                case Some( res ) => res.toLowerCase
            }

            val queryDocIds : List[ String ] = params.get( "docIds" )
              .map( _.split( "," ).map( _.trim ).toList ).toList.flatten

            val bodyDocIds : List[ String ] = request.body match {
                case "" => Nil
                case json => unmarshal( json, classOf[ List[ String ] ] ).getOrElse( throw new BadRequestBodyException( "Request body is not valid JSON list of strings" ) )
            }

            val docIds = queryDocIds ++ bodyDocIds

            if ( docIds.isEmpty ) throw new BadRequestBodyException( "request is missing docIds query parameter and body. Must include one or the other." )

            corpusTenantIndex.tenant( tenantId ).recoverWith {
                case _ => Future.failed( new ResourceNotFoundException( "tenant", Some( tenantId ) ) )
            } flatMap ( existingTenant => corpusTenantIndex.removeDocumentsFromTenant( docIds, existingTenant.id ) ) map { _ =>
                Ok()
            } recoverWith {
                case e : DocIdMissingFromTenantException =>
                    throw new ResourceNotFoundException( e.getMessage )
                case e : TenantNotFoundException => throw new ResourceNotFoundException( "tenantId", Some( tenantId ) )
                case e => Future.failed( e )
            } logged
        }
    } ) )


    post( "/:tenantId/clone/:newTenantId" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        RetrieveTenant.from( GlobalCorpus ).secureDart {
            CreateTenant.in( GlobalCorpus ).secureDart {
                val tenantId = params.get( "tenantId" ).map( _.trim.toLowerCase ) match {
                    case None => throw new BadQueryParameterException( "parameter tenantId is missing" )
                    case Some( "global" ) => throw new BadQueryParameterException( "can't clone global corpus" )
                    case Some( res ) => res.toLowerCase
                }

                val newTenantId = params.get( "newTenantId" ).map( _.trim.toLowerCase ) match {
                    case None => throw new BadQueryParameterException( "parameter newTenantId is missing" )
                    case Some( "global" ) => throw new BadQueryParameterException( "tenant already exists with id global" )
                    case Some( res ) => res.toLowerCase
                }

                corpusTenantIndex.cloneTenant( tenantId, newTenantId ) map { _ => Created() } recoverWith {
                    case _ : TenantNotFoundException =>
                        Future.failed( new ResourceNotFoundException( "tenant", Some( tenantId ) ) )
                    case _ : TenantAlreadyExistsException =>
                        Future.failed( new BadQueryParameterException( s"tenant already exists with id $newTenantId" ) )
                    case e : InvalidTenantIdException =>
                        Future.failed( new BadQueryParameterException( "newTenantId", Some( newTenantId ), {
                            val originalMessage = e.getMessage
                            val msgPrefix = "Acceptable format: "
                            val msgIndex = originalMessage.indexOf( "Acceptable format: " )
                            originalMessage.substring( msgIndex ).stripPrefix( msgPrefix )
                        } ) )
                    case e : NonAtomicTenantIndexFailureException =>
                        Future.failed( new BadQueryParameterException( s"${e.getMessage}\nOriginal exception: ${e.originalException.getMessage}\nRollback exception: ${e.recoveryFailure.getMessage}" ) )
                }
            }
        }
    } ) )
}
