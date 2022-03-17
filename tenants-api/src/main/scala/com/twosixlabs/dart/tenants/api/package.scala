package com.twosixlabs.dart.tenants

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

package object api {

    val m : ObjectMapper = {
        val mapper = new ObjectMapper()
        mapper.registerModule( DefaultScalaModule )
        mapper.registerModule( new JavaTimeModule )
        mapper
    }

}
