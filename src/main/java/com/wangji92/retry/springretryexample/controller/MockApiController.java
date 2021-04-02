package com.wangji92.retry.springretryexample.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 模拟服务不正常
 *
 * @author 汪小哥
 * @date 02-04-2021
 */
@RestController
public class MockApiController {

    /**
     * 模拟服务不正常
     *
     * @param status
     * @return
     */
    @GetMapping("/unstableApi/{status}")
    public ResponseEntity<Integer> unstableApi(@PathVariable int status) {
        if (INTERNAL_SERVER_ERROR.value() == status) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }
        if (UNAUTHORIZED.value() == status) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return ResponseEntity.ok(status);
    }

}
