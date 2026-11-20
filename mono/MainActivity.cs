// Android entry point — MonoGame AndroidGameActivity host.
using Android.App;
using Android.Content.PM;
using Android.OS;
using Android.Views;

namespace TipsyBird.Mono
{
    [Activity(Label = "Tipsy Bird 2D",
              MainLauncher = true,
              Icon = "@mipmap/ic_launcher",
              ScreenOrientation = ScreenOrientation.SensorLandscape,
              ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize | ConfigChanges.KeyboardHidden,
              Theme = "@android:style/Theme.NoTitleBar.Fullscreen")]
    public class MainActivity : Microsoft.Xna.Framework.AndroidGameActivity
    {
        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);
            var g = new TipsyGame();

            var view = g.Services.GetService(typeof(View)) as View;
            SetContentView(view);

            g.Run();
        }
    }
}
