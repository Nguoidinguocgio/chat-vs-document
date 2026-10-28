package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Tham số bind kiểu varchar khiến PostgreSQL so sánh text = text (phân biệt hoa/thường);
    // ép sang citext để dùng đúng toán tử không phân biệt hoa/thường và chỉ mục unique.
    @Query(value = "SELECT * FROM users WHERE email = CAST(:email AS citext)", nativeQuery = true)
    Optional<User> findByEmail(@Param("email") String email);
}
