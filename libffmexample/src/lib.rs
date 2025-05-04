use std::slice;

#[unsafe(no_mangle)]
pub extern "C" fn sum(array_ptr: *mut f64, length: usize) -> f64 {
    let arr = unsafe { slice::from_raw_parts(array_ptr, length) };

    let mut sum = 0.0;
    for &value in arr.iter() {
        sum += value;
    }

    sum
}

/// Type alias for the java comparator function pointer
type Comparator = extern "C" fn(a: f64, b: f64) -> i32;

#[unsafe(no_mangle)]
pub extern "C" fn quicksort(array_ptr: *mut f64, length: usize, cmp: Comparator) {
    let arr = unsafe { slice::from_raw_parts_mut(array_ptr, length) };

    quicksort_recursive(arr, &cmp);
}

fn quicksort_recursive(arr: &mut [f64], cmp: &Comparator) {
    if arr.len() <= 1 {
        return;
    }

    let pivot_index = partition(arr, cmp);
    quicksort_recursive(&mut arr[0..pivot_index], cmp);
    quicksort_recursive(&mut arr[pivot_index + 1..], cmp);
}

fn partition(arr: &mut [f64], cmp: &Comparator) -> usize {
    let len = arr.len();
    let pivot = arr[len - 1];
    let mut i = 0;
    for j in 0..len - 1 {
        if cmp(arr[j], pivot) < 0 {
            arr.swap(i, j);
            i += 1;
        }
    }
    arr.swap(i, len - 1);
    i
}

/*
 * Here for the second example / struct Regions
 */
use std::os::raw::c_char;

#[repr(C)]
pub struct DataPoint {
    pub temperature: f32,
    pub humidity: f32,
    pub wind_speed: f32,
}

#[repr(C)]
pub struct Region {
    pub city_name: *const c_char,
    pub data_points: *const DataPoint,
    pub data_points_len: usize,
}

#[repr(C)]
pub struct RegionSequence {
    pub regions: *const Region,
    pub regions_len: usize,
}

unsafe fn average_temperature(region: &Region) -> Option<f32> {
    if region.data_points.is_null() || region.data_points_len == 0 {
        return None;
    }

    let slice = unsafe { slice::from_raw_parts(region.data_points, region.data_points_len) };

    Some(slice.iter().map(|dp| dp.temperature).sum::<f32>() / slice.len() as f32)
}

#[unsafe(no_mangle)]
pub extern "C" fn find_warmest_region(seq: *const RegionSequence) -> *const Region {
    if seq.is_null() {
        return std::ptr::null();
    }

    let regions = unsafe {&*seq};
    if regions.regions.is_null() || regions.regions_len == 0 {
        return std::ptr::null();
    }

    let region_slice = unsafe { slice::from_raw_parts(regions.regions, regions.regions_len) };
    let mut warmest: Option<(&Region, f32)> = None;

    for region in region_slice {
        if let Some(avg_temp) = unsafe { average_temperature(region) } {
            if warmest.is_none() || avg_temp > warmest.unwrap().1 {
                warmest = Some((region, avg_temp));
            }
        }
    }

    warmest.map_or(std::ptr::null(), |(region, _)| region as *const Region)
}
