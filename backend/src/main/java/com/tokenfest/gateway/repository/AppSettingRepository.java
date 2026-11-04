package com.tokenfest.gateway.repository;

import com.tokenfest.gateway.domain.AppSetting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

}
