use jni::{sys::jstring, JNIEnv};

#[no_mangle]
pub extern "C" fn test(env: JNIEnv) -> jstring {
    env.new_string("hi".to_string()).unwrap().into_raw()
}
