package com.foo.rest.examples.spring.openapi.v3.headerobject

import com.foo.rest.examples.spring.openapi.v3.gson.FooDto
import com.google.gson.Gson
import org.apache.commons.io.IOUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping(path = ["/api/headerobject"])
open class HeaderObjectRest {

    @GetMapping
    open fun get(@RequestHeader token: Token) : ResponseEntity<String> {

        if(token.counter > 0 && token.x.length > 0) {
            return ResponseEntity.ok("OK")
        }

        return ResponseEntity.status(400).build()
    }
}



class Token{
    var counter : Int = 0
    var x : String = ""
}