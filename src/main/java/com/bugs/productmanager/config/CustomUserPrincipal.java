package com.bugs.productmanager.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class CustomUserPrincipal extends User {

    private final String company;
    private final String department;
    private final String team;

    public CustomUserPrincipal(String username, String password, boolean enabled,
                               Collection<? extends GrantedAuthority> authorities,
                               String company, String department, String team) {
        super(username, password, enabled, true, true, true, authorities);
        this.company = company;
        this.department = department;
        this.team = team;
    }

    public String getCompany() { return company; }
    public String getDepartment() { return department; }
    public String getTeam() { return team; }
}
