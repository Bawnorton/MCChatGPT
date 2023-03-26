package com.bawnorton.mcchatgpt.config;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Config {
    private static Config INSTANCE;

    @Expose
    @SerializedName("encrypted_token")
    public String token;

    @Expose
    @SerializedName("secret")
    public String secret;

    @Expose
    @SerializedName("context_level")
    public Integer contextLevel;

    private Config() {
    }

    public static Config getInstance() {
        if (INSTANCE == null) INSTANCE = new Config();
        return INSTANCE;
    }

    public static void update(Config config) {
        INSTANCE = config;
    }
}
