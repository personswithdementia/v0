
//< call a JNI block

#include <cmath>
enum Fade {
	IN,
	OUT,
	CROSFADE,
};

enum FadeCurve {
	Linear,
	Exponential,
	Logarithmic,
	S_Curve,
};

// < Thread this audio, TODO
struct AudioBlock {
	char *name;  // audio name
	enum Fade *pp;
	FadeCurve CurveAudio();
	float (*DurationSeconds)(int area, );
	float (*StartTimeSeconds)();
};

// < WHAT happens when the audio curves
