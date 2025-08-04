package com.example.drools.model;

public class Customer {
    private int age;

    public Customer() {
    }

    public Customer(int age) {
        this.age = age;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "Customer{age=" + age + '}';
    }
}