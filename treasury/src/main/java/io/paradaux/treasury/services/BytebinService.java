package io.paradaux.treasury.services;

public interface BytebinService {

    /**
     * Uploads {@code content} to Bytebin with the given MIME type.
     *
     * @return the full URL where the content can be retrieved
     */
    String upload(String content, String contentType);
}
