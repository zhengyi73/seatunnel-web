/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.app.interceptor;

import org.apache.seatunnel.app.common.Constants;
import org.apache.seatunnel.app.dal.dao.IUserDao;
import org.apache.seatunnel.app.dal.entity.User;
import org.apache.seatunnel.app.dal.entity.UserLoginLog;
import org.apache.seatunnel.app.security.JwtUtils;
import org.apache.seatunnel.app.security.UserContext;
import org.apache.seatunnel.common.access.AccessInfo;

import org.apache.commons.lang3.StringUtils;

import org.eclipse.jetty.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.Objects;

import static io.jsonwebtoken.Claims.EXPIRATION;
import static org.apache.seatunnel.server.common.Constants.OPTIONS;
import static org.apache.seatunnel.server.common.Constants.TOKEN;
import static org.apache.seatunnel.server.common.Constants.USER_ID;

@Slf4j
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Resource private IUserDao userDaoImpl;

    @Resource private JwtUtils jwtUtils;

    // @Override
    @SuppressWarnings("MagicNumber")
    public boolean preHandle1(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getMethod().equals(OPTIONS)) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Headers", "*");
            response.setHeader("Access-Control-Allow-Methods", "*");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Max-Age", "3600");
            return true;
        }

        long currentTimestamp = System.currentTimeMillis();
        final String token = request.getHeader(TOKEN);
        if (StringUtils.isBlank(token)) {
            log.info("user does not exist");
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            return false;
        }
        final Map<String, Object> map = jwtUtils.parseToken(token);
        final Integer userId = (Integer) map.get(USER_ID);
        if (Objects.isNull(userId)) {
            log.info("userId does not exist");
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            return false;
        }
        long workspaceIdFromToken = ((Number) map.get("workspaceId")).longValue();
        final UserLoginLog userLoginLog = userDaoImpl.getLastLoginLog(userId, workspaceIdFromToken);
        if (Objects.isNull(userLoginLog) || !userLoginLog.getTokenStatus()) {
            log.info("userLoginLog does not exist");
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            return false;
        }

        final Integer expireDate = (Integer) map.get(EXPIRATION);
        if (Objects.isNull(expireDate) || currentTimestamp - (long) expireDate * 1000 > 0) {
            log.info("user token has expired");
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            return false;
        }

        map.forEach(request::setAttribute);
        User user = new User();
        user.setUsername((String) map.get("name"));
        user.setId((Integer) map.get("id"));
        log.debug(
                "Setting user to request attributes: userId={}, username={}",
                user.getId(),
                user.getUsername());

        UserContext userContext = new UserContext();
        userContext.setUser(user);
        userContext.setWorkspaceId(workspaceIdFromToken);

        AccessInfo accessInfo = new AccessInfo();
        accessInfo.setUsername(user.getUsername());
        accessInfo.setWorkspaceName((String) map.get("workspaceName"));
        userContext.setAccessInfo(accessInfo);

        request.setAttribute(Constants.SESSION_USER_CONTEXT, userContext);

        request.setAttribute("userId", userId);
        return true;
    }

    @Override
    @SuppressWarnings("MagicNumber")
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getMethod().equals(OPTIONS)) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Headers", "*");
            response.setHeader("Access-Control-Allow-Methods", "*");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Max-Age", "3600");
            return true;
        }

        final String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization)) {
            log.info("Authorization header does not exist, trying fallback authentication");
            // 尝试使用 preHandle1 作为后备验证
            return preHandle1(request, response, handler);
        }

        // 解析 Bearer token
        final String token;
        if (authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        } else {
            log.info("Invalid Authorization format, trying fallback authentication");
            // 尝试使用 preHandle1 作为后备验证
            return preHandle1(request, response, handler);
        }

        // 使用 Sa-Token 验证 token（与 login-auth-api 共享认证）
        Object loginId;
        try {
            loginId = StpUtil.getLoginIdByToken(token);
        } catch (Exception e) {
            log.info(
                    "Sa-Token validation failed: {}, trying fallback authentication",
                    e.getMessage());
            // Sa-Token 验证失败，尝试使用 preHandle1 作为后备验证
            return preHandle1(request, response, handler);
        }

        if (Objects.isNull(loginId)) {
            log.info("loginId does not exist for token, trying fallback authentication");
            // 尝试使用 preHandle1 作为后备验证
            return preHandle1(request, response, handler);
        }

        // 从 token session 中获取用户信息
        String userName = String.valueOf(loginId);
        try {
            // 尝试从 token session 获取用户名
            Object userNameObj = StpUtil.getExtra("userName");
            if (userNameObj != null) {
                userName = String.valueOf(userNameObj);
            }
        } catch (Exception e) {
            log.debug("Failed to get userName from token session: {}", e.getMessage());
        }

        // 构建用户对象
        User user = new User();
        user.setUsername(userName);
        // 将 loginId 转换为 Integer（如果是数字字符串）
        try {
            user.setId(Integer.parseInt(String.valueOf(loginId)));
        } catch (NumberFormatException e) {
            user.setId(0); // 默认 ID
        }

        log.debug(
                "Setting user to request attributes: userId={}, username={}",
                user.getId(),
                user.getUsername());

        UserContext userContext = new UserContext();
        userContext.setUser(user);
        // 默认 workspaceId 为 1
        userContext.setWorkspaceId(1L);

        AccessInfo accessInfo = new AccessInfo();
        accessInfo.setUsername(user.getUsername());
        accessInfo.setWorkspaceName("default");
        userContext.setAccessInfo(accessInfo);

        request.setAttribute(Constants.SESSION_USER_CONTEXT, userContext);
        request.setAttribute("userId", user.getId());

        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView)
            throws Exception {
        // do nothing
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        // do nothing
    }
}
