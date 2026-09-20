package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.UserResponseDTO;
import pe.edu.utec.atlasrambackend.model.User;

@Component
public class UserMapper {

    public UserResponseDTO toResponse(User u) {
        return new UserResponseDTO(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getRole(),
                u.isActive(),
                u.getCreatedAt()
        );
    }
}