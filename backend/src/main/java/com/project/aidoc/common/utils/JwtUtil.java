// 
// import io.jsonwebtoken.Claims;
// import io.jsonwebtoken.Jwts;
// import io.jsonwebtoken.SignatureAlgorithm;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Component;
// 
// import java.util.Date;
// 
// @Component
// public class JwtUtil {
// 
//     @Value("${jwt.secret:aidoc_secret_key}")
//     private String secret;
// 
//     @Value("${jwt.expiration:86400}")
//     private Long expiration; // 过期时间，单位秒，默认24小时
// 
//     /**
//      * 生成JWT token
//      */
//     public String generateToken(String username) {
//         Date now = new Date();
//         Date expiryDate = new Date(now.getTime() + expiration * 1000);
// 
//         return Jwts.builder()
//                 .setSubject(username)
//                 .setIssuedAt(new Date())
//                 .setExpiration(expiryDate)
//                 .signWith(SignatureAlgorithm.HS512, secret)
//                 .compact();
//     }
// 
//     /**
//      * 解析JWT token
//      */
//     public Claims getClaimsFromToken(String token) {
//         try {
//             return Jwts.parser()
//                     .setSigningKey(secret)
//                     .parseClaimsJws(token)
//                     .getBody();
//         } catch (Exception e) {
//             return null;
//         }
//     }
// 
//     /**
//      * 验证JWT token是否有效
//      */
//     public Boolean validateToken(String token) {
//         try {
//             Claims claims = getClaimsFromToken(token);
//             return claims != null && !claims.getExpiration().before(new Date());
//         } catch (Exception e) {
//             return false;
//         }
//     }
// 
//     /**
//      * 从JWT token中获取用户名
//      */
//     public String getUsernameFromToken(String token) {
//         try {
//             Claims claims = getClaimsFromToken(token);
//             return claims.getSubject();
//         } catch (Exception e) {
//             return null;
//         }
//     }
// }
