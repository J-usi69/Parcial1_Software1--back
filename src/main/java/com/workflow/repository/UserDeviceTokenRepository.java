package com.workflow.repository;

import com.workflow.domain.model.UserDeviceToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceTokenRepository extends MongoRepository<UserDeviceToken, String> {
    
    List<UserDeviceToken> findByUsuarioId(String usuarioId);
    
    Optional<UserDeviceToken> findByToken(String token);
    
    void deleteByToken(String token);
}
