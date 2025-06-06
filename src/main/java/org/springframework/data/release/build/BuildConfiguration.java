/*
 * Copyright 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.release.build;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.plugin.core.PluginRegistry;

import org.xmlbeam.XBProjector;
import org.xmlbeam.XBProjector.Flags;
import org.xmlbeam.config.DefaultXMLFactoriesConfig;
import org.xmlbeam.config.DefaultXMLFactoriesConfig.NamespacePhilosophy;

/**
 * Spring configuration for build related components.
 *
 * @author Oliver Gierke
 */
@Configuration(proxyBeanMethods = false)
class BuildConfiguration {

	@Bean
	public PluginRegistry<BuildSystem, SupportedProject> buildSystems(List<? extends BuildSystem> buildSystems) {
		return PluginRegistry.of(buildSystems);
	}

	@Bean
	public XBProjector projectionFactory() {

		DefaultXMLFactoriesConfig config = new DefaultXMLFactoriesConfig();
		config.setNamespacePhilosophy(NamespacePhilosophy.AGNOSTIC);
		config.setOmitXMLDeclaration(false);
		config.setPrettyPrinting(false);

		return new XBProjector(config, Flags.TO_STRING_RENDERS_XML);
	}

}
