package com.rmschots.question;

import java.net.URI;

public abstract class Request {
    public abstract URI getUri();
    public abstract Response call();
}
