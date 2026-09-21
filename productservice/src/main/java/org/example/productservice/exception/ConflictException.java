package org.example.productservice.exception;

public class ConflictException extends RuntimeException{
    public ConflictException(String message){super(message);}
}