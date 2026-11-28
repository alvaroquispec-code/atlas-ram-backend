package pe.edu.utec.atlasrambackend.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Conversor JPA: cifra al guardar y descifra al leer con FieldCipher. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        FieldCipher cipher = FieldCipher.active();
        return cipher == null ? attribute : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        FieldCipher cipher = FieldCipher.active();
        return cipher == null ? dbData : cipher.decrypt(dbData);
    }
}
