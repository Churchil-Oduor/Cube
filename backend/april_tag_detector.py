import cv2 as cv
from pupil_apriltags import Detector
import numpy as np

class AprilTagDetector:
    """Detects apriltags"""
    fx, fy = 600, 600
    cx, cy = 320, 240
    camera_params = [fx, fy, cx, cy]

    def __init__(self, tag_size=0.165):
        self.__tag_size = tag_size
        self.__detector = Detector(
        families = "tag36h11",
        nthreads=1,
        quad_decimate=1.0,
        quad_sigma=0.0,
        refine_edges=1,
        decode_sharpening=0.25,
        debug=0
        )

    def pose_detected(self, frame):
        gray = cv.cvtColor(frame, cv.COLOR_BGR2GRAY)
        tags = self.__detector.detect(
                gray,
                estimate_tag_pose = True,
                camera_params = self.camera_params,
                tag_size = self.__tag_size)

        for tag in tags:
            pose_R = tag.pose_R
            pose_t = tag.pose_t
            axis_length = self.__tag_size * 0.5
            axis_points = np.float32([
                [0, 0, 0], #origin
                [axis_length, 0, 0], #X-axis
                [0, axis_length, 0], #Y-axis
                [0, 0, axis_length] # Z-axis
                ]).reshape(-1, 3)

            camera_matrix = np.array([

                [self.fx, 0, self.cx],
                [0, self.fy, self.cy],
                [0, 0, 1]
                ], dtype=np.float32)

            dist_coeffs = np.zeros((4, 1))
            axis_2d, _ = cv.projectPoints(
                    axis_points,
                    pose_R,
                    pose_t,
                    camera_matrix,
                    dist_coeffs
                    )
            axis_2d = axis_2d.reshape(-1, 2).astype(int)
            origin = tuple(axis_2d[0])
            co_ordinates = {
                    "origin": axis_2d[0],
                    "x-axis":  axis_2d[1],
                    "y-axis": axis_2d[2],
                    "z-axis": axis_2d[3]
                }

            return co_ordinates
