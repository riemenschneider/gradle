/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CustomComponentSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
    @Managed interface SampleLibrary extends ComponentSpec {}
    @Managed interface SampleBinary extends BinarySpec {}

    interface LibrarySourceSet extends LanguageSourceSet {}
    class DefaultLibrarySourceSet extends BaseLanguageSourceSet implements LibrarySourceSet { }

    class MyBinaryDeclarationModel implements Plugin<Project> {
        void apply(final Project project) {}

        static class ComponentModel extends RuleSource {
            @ComponentType
            void register(ComponentTypeBuilder<SampleLibrary> builder) {}

            @BinaryType
            void register(BinaryTypeBuilder<SampleBinary> builder) {}

            @LanguageType
            void registerSourceSet(LanguageTypeBuilder<LibrarySourceSet> builder) {
                builder.setLanguageName("librarySource")
                builder.defaultImplementation(DefaultLibrarySourceSet)
            }
        }
    }

    apply plugin:MyBinaryDeclarationModel
"""
    }

    def "source order is retained"() {
        buildFile << '''
class Dump extends RuleSource {
    @Mutate
    void tasks(ModelMap<Task> tasks, ModelMap<BinarySpec> binaries) {
        tasks.create("verify") {
            doLast {
                binaries.each { binary ->
                    println "Binary sources: ${binary.sources.values()}"
                    println "Binary inputs: ${binary.inputs}"
                }
            }
        }
    }
}

apply plugin: Dump

model {
    components {
        sampleLib(SampleLibrary) {
            sources {
                compA(LibrarySourceSet) {
                    source.srcDir "src/main/comp-a"
                }
                compB(LibrarySourceSet) {
                    source.srcDir "src/main/comp-b"
                }
            }
            binaries {
                bin(SampleBinary) {
                    sources {
                        binaryA(LibrarySourceSet) {
                            source.srcDir "src/main/binary-a"
                        }
                        binaryB(LibrarySourceSet) {
                            source.srcDir "src/main/binary-b"
                        }
                    }
                }
            }
            sources {
                compC(LibrarySourceSet) {
                    source.srcDir "src/main/comp-c"
                }
            }
            binaries {
                bin {
                    sources {
                        binaryC(LibrarySourceSet) {
                            source.srcDir "src/main/binary-c"
                        }
                    }
                }
            }
        }
    }
    components {
        sampleLib {
            sources {
                compD(LibrarySourceSet) {
                    source.srcDir "src/main/comp-d"
                }
            }
            binaries {
                bin {
                    sources {
                        binaryD(LibrarySourceSet) {
                            source.srcDir "src/main/binary-d"
                        }
                    }
                }
            }
        }
    }
}
'''
        expect:
        succeeds "verify"
        output.contains """Binary sources: [LibrarySourceSet 'sampleLib:binaryA', LibrarySourceSet 'sampleLib:binaryB', LibrarySourceSet 'sampleLib:binaryC', LibrarySourceSet 'sampleLib:binaryD']"""
        output.contains """Binary inputs: [LibrarySourceSet 'sampleLib:compA', LibrarySourceSet 'sampleLib:compB', LibrarySourceSet 'sampleLib:compC', LibrarySourceSet 'sampleLib:compD', LibrarySourceSet 'sampleLib:binaryA', LibrarySourceSet 'sampleLib:binaryB', LibrarySourceSet 'sampleLib:binaryC', LibrarySourceSet 'sampleLib:binaryD']"""
    }

    def "fail when multiple source sets are registered with the same name"() {
        buildFile << """
model {
    components {
        sampleLib(SampleLibrary) {
            binaries {
                bin(SampleBinary) {
                    sources {
                        main(LibrarySourceSet) {
                            source.srcDir "src/main/lib"
                        }
                    }
                }
            }
        }
        sampleLib {
            binaries.bin.sources {
                main(LibrarySourceSet) {
                    source.srcDir "src/main/lib"
                }
            }
        }
    }
}
"""
        when:
        fails("components")

        then:
        failure.assertHasCause("Exception thrown while executing model rule: sampleLib { ... } @ build.gradle line 41, column 9")
        failure.assertHasCause("Cannot create 'components.sampleLib.binaries.bin.sources.main' using creation rule 'sampleLib { ... } @ build.gradle line 41, column 9 > components.sampleLib.getBinaries() > create(main)' as the rule 'sampleLib(SampleLibrary) { ... } @ build.gradle line 30, column 9 > create(bin) > create(main)' is already registered to create this model element.")
    }

    def "user can attach unmanaged internal views to custom `LanguageSourceSet`"() {
        given:
        buildFile << """
            interface HaxeSourceSet extends LanguageSourceSet {
                String getPublicData()
                void setPublicData(String data)
            }
            interface HaxeSourceSetInternal {
                String getInternalData()
                void setInternalData(String data)
            }
            class DefaultHaxeSourceSet extends BaseLanguageSourceSet implements HaxeSourceSet, HaxeSourceSetInternal {
                String publicData
                String internalData
            }

            class HaxeRules extends RuleSource {
                @LanguageType
                void registerHaxeLanguageSourceSetType(LanguageTypeBuilder<HaxeSourceSet> builder) {
                    builder.setLanguageName("haxe")
                    builder.defaultImplementation(DefaultHaxeSourceSet)
                    builder.internalView(HaxeSourceSetInternal)
                }
            }
            apply plugin: HaxeRules

            model {
                components {
                    sampleLib(SampleLibrary) {
                        binaries {
                            sampleBin(SampleBinary) {
                                sources {
                                    haxe(HaxeSourceSet) {
                                        publicData = "public"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            class TestRules extends RuleSource {
                @Defaults
                void useInternalView(@Path("components.sampleLib.binaries.sampleBin.sources.haxe") HaxeSourceSetInternal lss) {
                    lss.setInternalData("internal")
                }
            }
            apply plugin: TestRules

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components*.name == ["sampleLib"]
                        assert components.withType(SampleLibrary)*.name == ["sampleLib"]
                        assert components.sampleLib.binaries.sampleBin.sources.haxe != null
                        assert components.sampleLib.binaries.sampleBin.sources.haxe.publicData == "public"
                        assert components.sampleLib.binaries.sampleBin.sources.haxe.internalData == "internal"
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }

    def "fails on registration when model type extends `LanguageSourceSet` without a default implementation"() {
        given:
        buildFile << """
            interface HaxeSourceSet extends LanguageSourceSet {}
            class HaxeRules extends RuleSource {
                @LanguageType
                void registerHaxeLanguageSourceSetType(LanguageTypeBuilder<HaxeSourceSet> builder) {
                    builder.setLanguageName("haxe")
                }
            }
            apply plugin: HaxeRules
            model {
                components {
                    sampleLib(SampleLibrary) {
                        binaries {
                            sampleBin(SampleBinary) {
                                sources {
                                    haxe(HaxeSourceSet)
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        fails "model"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: LanguageBasePlugin.Rules#registerSourceSetTypes")
        failure.assertHasCause("No implementation type registered for 'HaxeSourceSet'")
    }
}
