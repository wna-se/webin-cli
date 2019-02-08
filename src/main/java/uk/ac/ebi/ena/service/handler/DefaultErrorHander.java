package uk.ac.ebi.ena.service.handler;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import uk.ac.ebi.ena.webin.cli.WebinCli;
import uk.ac.ebi.ena.webin.cli.WebinCliException;

import java.io.IOException;

import static org.springframework.http.HttpStatus.Series.CLIENT_ERROR;
import static org.springframework.http.HttpStatus.Series.SERVER_ERROR;

public class DefaultErrorHander implements ResponseErrorHandler {

    private final String systemError;

    public DefaultErrorHander(String systemError) {
        this.systemError = systemError;
    }

    @Override
    public boolean hasError(ClientHttpResponse httpResponse) throws IOException {
        return (httpResponse.getStatusCode().series() == CLIENT_ERROR ||
                httpResponse.getStatusCode().series() == SERVER_ERROR);
    }

    @Override
    public void handleError(ClientHttpResponse httpResponse) throws IOException {
        switch (httpResponse.getStatusCode()) {
            case UNAUTHORIZED:
            case FORBIDDEN:
                throw WebinCliException.createUserError(WebinCli.AUTHENTICATION_ERROR);
            default:
                throw WebinCliException.createSystemError(systemError);
        }
    }
}
