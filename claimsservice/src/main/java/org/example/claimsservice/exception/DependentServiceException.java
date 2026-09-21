package org.example.claimsservice.exception;

public class DependentServiceException extends RuntimeException{
    public DependentServiceException(String message){
        super(message);
    }
}