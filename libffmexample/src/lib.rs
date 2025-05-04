use std::slice;

#[unsafe(no_mangle)]
pub extern "C" fn sum(array_ptr: *mut f64, length: usize) -> f64 {
    let arr = unsafe { slice::from_raw_parts(array_ptr, length) };

    let mut sum = 0.0;
    for value in arr {
        sum += value;
    }

    sum
}

/// Type alias for the java comparator function pointer
type Comparator = extern "C" fn(a: f64, b: f64) -> i32;

#[unsafe(no_mangle)]
pub extern "C" fn quicksort(array_ptr: *mut f64, length: usize, cmp: Comparator) {
    let arr = unsafe { slice::from_raw_parts_mut(array_ptr, length) };

    quicksort_recursive(arr, cmp);
}

fn quicksort_recursive(arr: &mut [f64], cmp: Comparator) {
    if arr.len() <= 1 {
        return;
    }

    let pivot_index = partition(arr, cmp);
    quicksort_recursive(&mut arr[0..pivot_index], cmp);
    quicksort_recursive(&mut arr[pivot_index + 1..], cmp);
}

fn partition(arr: &mut [f64], cmp: Comparator) -> usize {
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

impl Region {
    fn average_temperature(&self) -> Option<f32> {
        if self.data_points.is_null() || self.data_points_len == 0 {
            return None;
        }

        let data_points = unsafe { slice::from_raw_parts(self.data_points, self.data_points_len) };
        Some(data_points.iter().map(|dp| dp.temperature).sum::<f32>() / data_points.len() as f32)
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn find_warmest_region(seq: *const RegionSequence) -> *const Region {
    if seq.is_null() {
        return std::ptr::null();
    }

    let region_sequence = unsafe { &*seq };
    if region_sequence.regions.is_null() || region_sequence.regions_len == 0 {
        return std::ptr::null();
    }

    let regions = unsafe { 
        slice::from_raw_parts(region_sequence.regions, region_sequence.regions_len) 
    };

    regions
        .iter()
        .filter_map(|region| region.average_temperature().map(|temp| (region, temp)))
        .max_by(|(_, temp_a), (_, temp_b)| {
            temp_a
                .partial_cmp(temp_b)
                .unwrap_or(std::cmp::Ordering::Equal)
        })
        .map_or(std::ptr::null(), |(region, _)| region)
}
