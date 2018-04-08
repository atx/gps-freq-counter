
// Fixing something in this mess will be... challenging...

$fn = 30;
PCB_WIDTH = 50.0;
PCB_HEIGHT = 47.5;
PCB_THICKNESS = 1.6;
HOLE_OFFSET = 4.0;
HOLE_XDELTA = PCB_WIDTH/2.0 - HOLE_OFFSET;
HOLE_YDELTA = PCB_HEIGHT/2.0 - HOLE_OFFSET;
SCREW_RADIUS = 1.0;
SMA_RADIUS = 3.0;
OLED_WIDTH = 27.0;
OLED_HEIGHT = 14.5;
BUTTON_X = -11.2;
BUTTON_Y = 16.6;

module rounded_cube(xd, yd, h, r) {
  hull() {
    for (x = [-1, 1]) {
      for (y = [-1, 1]) {
        translate([xd*x, yd*y, 0])
          cylinder(r = r, h = h, center = true);
      }
    }
  }
}

module sma() {
  CUBE_HEIGHT = 1.9;
  translate([0, 0, CUBE_HEIGHT/2])
    cylinder(r = SMA_RADIUS, h = 11);
  cube([7.5, 7.5, CUBE_HEIGHT], center = true);
  PIN_LENGTH = 4.0;
  for (x = [0, 1]) {
    for (y = [-1, 1]) {
      translate([x*3, y*3, -PIN_LENGTH/2 - CUBE_HEIGHT/2])
        cube([1.1, 1.1, PIN_LENGTH], center = true);
    }
  }
  translate([0, 0, -PIN_LENGTH/2 - CUBE_HEIGHT/2])
    cylinder(r = 0.5, h = PIN_LENGTH, center = true);
}

module pcb() {
  color([0.2, 0.2, 0.6])
  difference() {
    hull() {
      for (x = [-1, 1]) {
        for (y = [-1, 1]) {
          translate([HOLE_XDELTA*x, HOLE_YDELTA*y, 0])
            cylinder(r = 4, h = PCB_THICKNESS, center = true);
        }
      }
    }
    for (x = [-1, 1]) {
      for (y = [-1, 1]) {
        translate([HOLE_XDELTA*x, HOLE_YDELTA*y, 0])
          cylinder(r = SCREW_RADIUS, h = PCB_THICKNESS*1.2, center = true);
      }
    }
  }
  // Connectors
  union() {
    // USB
    color([0.5, 0.5, 0.5])
      translate([24, -8.5, PCB_THICKNESS/2+3.2/2])
      cube([6, 8.2, 3.2], center = true);
    // SMAs
    translate([26, 6.9, 1])
      rotate([0, 90, 0])
      sma();
    translate([-26, 0, 1])
      rotate([0, -90, 0])
      sma();
  }
  // OLED
  translate([0, -6, PCB_THICKNESS/2 + 7]) {
    color([0.0, 0.0, 0.0])
      cube([28, 27, 3], center = true);
    translate([0, -6 + 4.5, 3/2])
      color([0.7, 0.7, 0.7])
      cube([OLED_WIDTH, OLED_HEIGHT, 1], center = true);
  }
  // Button
  color([0.0, 0.0, 0.0])
  translate([BUTTON_X, BUTTON_Y, 2.0])
  rotate([0, 0, 45]) {
    cube([6, 6, 4], center = true);
    translate([0, 0, 4/2])
      cylinder(r = 1.75, h = 6.5);
  }
  // JTAG connector
  translate([3.1, 15.2, PCB_THICKNESS/2])
    color([0, 0, 0])
    cube([13, 5, 9]);
}


module connector_holes(height) {
  for (c = [[26, 6.9], [-26, 0]]) {
    translate([c[0], c[1], height / 2 - 1.0])
      rotate([0, 90, 0])
      cylinder(r = SMA_RADIUS*1.3, h = 10, center = true);
  }
  translate([27, -8.5, height / 2 + 0.5])
    minkowski() {
      cube([5.5, 7.8, 3.2], center = true);
      rotate([0, 90, 0])
        cylinder(r = 1.5, h = 1);
    }
}

module screw_holes() {
  cone_height = SCREW_RADIUS * 1.25;
  shaft_height = 5.0 - cone_height;
  for (x = [-HOLE_XDELTA, HOLE_XDELTA]) {
    for (y = [-HOLE_YDELTA, HOLE_YDELTA]) {
      translate([x, y, -cone_height / 2 - 2.5]) {
        cylinder(r = SCREW_RADIUS*1.2, h = shaft_height, center = true);
        translate([0, 0, shaft_height/2])
          cylinder(r1 = SCREW_RADIUS, r2 = SCREW_RADIUS*2.5, h = cone_height);
      }
    }
  }
}


module half_sphere(r) {
  difference() {
    sphere(r = r);
    translate([0, 0, -r/2]) {
      cube([r*2, r*2, r], center = true);
    }
  }
}

BOX_EXTRA_SIZE = 4;
BOX_ROUNDING = 1;

module box_base(height, bottom) {
  translate([0, 0, -BOX_ROUNDING/2])
  difference() {
    minkowski() {
      rounded_cube(HOLE_XDELTA + (BOX_EXTRA_SIZE-BOX_ROUNDING), HOLE_YDELTA + (BOX_EXTRA_SIZE-BOX_ROUNDING),
          height - BOX_ROUNDING, 4);
      half_sphere(BOX_ROUNDING);
    }
    translate([0, 0, -bottom + BOX_ROUNDING])
      rounded_cube(HOLE_XDELTA*1.1, HOLE_YDELTA*1.05, height - BOX_ROUNDING, 4);
  }
}

module box_bottom() {
  HEIGHT = 9;
  BOTTOM = 2;
  difference() {
    mirror([0, 0, 1])
      box_base(HEIGHT, BOTTOM);
    translate([0, 0, BOTTOM])
      rounded_cube(HOLE_XDELTA*1.1, HOLE_YDELTA*1.05, HEIGHT, 4);

    translate([0, 0, -HEIGHT/2 - 0.001])
      mirror([0, 0, 1])
      screw_holes();
    connector_holes(HEIGHT);
  }
}

module box_top() {
  HEIGHT = 10;
  BOTTOM = 1.5;
  translate([0, 0, HEIGHT/2])
  difference() {
    box_base(HEIGHT, BOTTOM);

    translate([0, 0, HEIGHT/2 + 0.001])
      screw_holes();
    connector_holes(-HEIGHT);

    translate([0, -12 + 4.5, HEIGHT/2 - BOTTOM/2])
      minkowski() {
        cube([OLED_WIDTH - 2.2, OLED_HEIGHT - 2.2, BOTTOM*2], center = true);
        cylinder(r = 1.75, h = 1);
      }

    translate([BUTTON_X, BUTTON_Y, HEIGHT / 2]) {
      difference() {
        cylinder(r = 7.4, h = HEIGHT, center = true);
        cylinder(r = 5.5, h = HEIGHT, center = true);
        translate([-4, -4, 0])
          rotate([0, 0, 45])
          cube([7, 7, BOTTOM*2], center = true);
      }
    }
  }
}

pcb();

translate([0, 0, 9/2 - 2 - PCB_THICKNESS / 2 - 4])
  color([0.9, 0.5, 0])
  box_bottom();

translate([0, 0, 2])
  color([0.5, 0.9, 0], 1.0)
  box_top();
