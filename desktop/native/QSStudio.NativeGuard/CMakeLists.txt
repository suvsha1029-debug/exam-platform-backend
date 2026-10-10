cmake_minimum_required(VERSION 3.24)

project(QSStudioNativeGuard LANGUAGES CXX)

add_library(qsstudio_native_guard SHARED
    src/native_guard.cpp
)

target_compile_features(qsstudio_native_guard PRIVATE cxx_std_20)

if (MSVC)
    target_compile_options(qsstudio_native_guard PRIVATE /W4 /permissive-)
endif()

target_compile_definitions(qsstudio_native_guard PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
