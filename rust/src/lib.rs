use jni::JNIEnv;
use jni::sys::jstring;
use jni::objects::JObject;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_getString<'local>(
    env: JNIEnv,
    _obj: JObject<'local>,
) -> jstring {
    let s = String::from("Hello from Rust");
    env.new_string(s).unwrap().into_raw()
}