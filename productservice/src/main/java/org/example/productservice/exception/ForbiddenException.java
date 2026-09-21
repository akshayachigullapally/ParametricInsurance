package org.example.productservice.exception;

public class ForbiddenException extends RuntimeException{
    public ForbiddenException(String message){super(message);}
}