package org.example.productservice.exception;

public class DependentServiceException extends RuntimeException{
    public DependentServiceException(String message){
        super(message);
    }
}