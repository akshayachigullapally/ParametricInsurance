package org.example.financialservice.exception;

public class ConflictException extends RuntimeException{
    public ConflictException(String message){
        super(message);
    }
}