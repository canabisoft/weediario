package br.com.weediario.shared.application;

/**
 * 404. Também usada para recursos privados de outra pessoa, para não revelar que eles existem.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource) {
        super(resource + " não encontrado");
    }
}
