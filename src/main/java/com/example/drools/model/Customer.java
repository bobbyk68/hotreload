package com.example.drools.model;

public class Customer {
    private String name;
    private String type; // e.g., "VIP", "Regular"
    private int age;

    public Customer() {}

    public Customer(String name, String type, int age) {
        this.name = name;
        this.type = type;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getAge() {
        return age;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "Customer{name='" + name + "', type='" + type + "', age=" + age + '}';
    }
}
