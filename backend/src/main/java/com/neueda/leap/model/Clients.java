package com.neueda.leap;

public class Clients {
    private int client_id;
    private String first_name;
    private String last_name;
    private String email;
    public Clients()
    {
        client_id = 0;
        first_name = "";
        last_name = "";
        email = "";
    }

    public Clients(int client_id, String first_name, String last_name, String email)
    {
        this.client_id = client_id;
        this.first_name = first_name;
        this.last_name = last_name;
        this.email = email;
    }
}
