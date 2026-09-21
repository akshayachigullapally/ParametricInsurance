package org.example.triggerservice.exception;

public class DependentServiceException extends RuntimeException{
    public DependentServiceException(String message){
        super(message);
    }
}